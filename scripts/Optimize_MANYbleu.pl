#!/usr/bin/perl

#PBS -q trad -d . -V
#PBS -l nodes=1:ppn=5
#PBS -l mem=30g
#PBS -l cput=1000:00:00
#PBS -j oe -o first_run.log

use strict;
use warnings;
use File::Path;
use File::Copy "cp";
use File::Basename;
use Getopt::Long;
use Cwd;

########### VARIABLES DEFINITION

# Tools
my $_BIN_DIR="$ENV{HOME}/RELEASE/bin";
my $_MANY="$_BIN_DIR/MANY_501.jar";
my $_MANY_DIR="/lium/buster1/barrault/SRC/MANY";
my $_MANY_BLEU="$_MANY_DIR/scripts/MANYbleu.pl";

# Files
my $_MANY_CONFIG="many.config.xml";
my $_OUTPUT=undef;
my $_CURRENT_DIR=cwd();
my $_WORKING_DIR="syscomb_manybleu";
my @_PRIORS=();
my @_HYPOTHESES=(); 
foreach my $h (@_HYPOTHESES)
{
    push(@_PRIORS, 0.1);
}
my @_REFERENCES=();

#0ther variables
my $_NB_THREADS=5;
my $_NB_BACKBONES=-1;
my $_LOG_BASE=2.718;
my $_HELP=0;

# TERp parameters
my ($_DEL_COST, $_STEM_COST, $_SYN_COST, $_INS_COST, $_SUB_COST, $_MATCH_COST, $_SHIFT_COST) = (1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 1.0);
my $_SHIFT_CONSTRAINT = "relax";

my $_TOOLS_DIR="/lium/buster1/barrault/TOOLS";
my $_WORDNET = "$_TOOLS_DIR/WordNet-3.0/dict/";
my $_SHIFT_STOP_WORD_LIST = "$_TOOLS_DIR/terp/terp.v1/data/shift_word_stop_list.txt";
my $_PARAPHRASE_DB = "$_TOOLS_DIR/terp/terp.v1/data/phrases.db";
my $_TERP_PARAMS="terp.params";

# CONDOR
my $_CONDOR_INPUT="condor_manybleu.input";
my $_CONDOR_OUTPUT="condor_manybleu.output";
my $_CONDOR_STEPS="condor_manybleu.steps";
my $_CONDOR_ITERATION="condor_manybleu.iteration";
my $_CONDOR_BEST="condor_manybleu.best";


my %_CONFIG = (	
'many' => \$_MANY, 
'many-bleu' => \$_MANY_BLEU, 
'working-dir' => \$_WORKING_DIR,
'output' => \$_OUTPUT, 
'hyp' => \@_HYPOTHESES, 
'nb-backbones' => \$_NB_BACKBONES,
'reference' => \@_REFERENCES,
'wordnet' => \$_WORDNET, 'shift-stop-word-list' => \$_SHIFT_STOP_WORD_LIST,
'paraphrases' => \$_PARAPHRASE_DB, 'shift-constraint' => \$_SHIFT_CONSTRAINT,
'multithread' => \$_NB_THREADS,
'priors' => \@_PRIORS, 
'logBase' => \$_LOG_BASE, 'log-base' => \$_LOG_BASE, 
'--help' => \$_HELP
); 


my $usage = "Optimize_MANYdecode.pl \
--many <MANY.jar to use>             : default is $_MANY
--many-bleu                          : default is $_MANY_BLEU
--working-dir <working directory>    : default is $_WORKING_DIR \
--output <output file> \
--hyp <hypothesis file>              : repeat this param for each input hypotheses \
--nb-backbones <number of backbones> \
--reference <reference file>         : repeat this param for each references \
**** TERp PARAMETERS \
--wordnet <wordnet database> \
--shift-stop-word-list <list> \
--paraphrases <paraphrase db> \
--shift-constraint <constraint>      : default is $_SHIFT_CONSTRAINT, possible values are exact or relax \
--multithread <number of threads> \
--priors <systems priors> \
--log-base <base for log>            : default is $_LOG_BASE \

--help                               : print this help and exit \n";

########################
######################## Parsing parameters with GetOptions
$_HELP = 1 unless GetOptions(\%_CONFIG, 'many=s', 'many-bleu=s', 'working-dir=s', 'output=s', 'hyp=s@', 'nb-backbones=i', 'reference=s@',
'wordnet=s', 'shift-stop-word-list=s', 'paraphrases=s', 'shift-constraint=s',
'multithread=i', 'priors=f{,}', 
'log-base=f', 'help');

print "$0 running on ".`hostname -f`."\n";
if($_HELP || !defined $_OUTPUT || scalar @_HYPOTHESES < 2 || scalar @_REFERENCES < 1)
{
    print $usage;
    print "Please, give an output\n" unless (defined $_OUTPUT);
    print "Please, give several hypotheses\n" if (scalar @_HYPOTHESES < 2);
    print "Please, give at least one reference \n" if (scalar @_REFERENCES < 1);

    exit 1;
}


my $_CONDOR_BESTWEIGHTS="BEST.".basename($_OUTPUT).".align.costs";

########################

print STDOUT "    MANY - Open Source Machine Translation System Combination\n";

########################

my $_ITER=0;
if (-e $_CONDOR_ITERATION)
{
    open(ITER, "<$_CONDOR_ITERATION") or die "Can't read file $_CONDOR_ITERATION\n";
    $_ITER = <ITER>; chomp $_ITER;
    close(ITER);
	#on nettoie le répertoire de travail ...
    unlink glob	"$_WORKING_DIR/*.id";
}

print STDOUT " ******** ----- Iteration $_ITER ... ------- ********\n";

open(STEPS, ">>$_CONDOR_STEPS") or die "Can't open file $_CONDOR_STEPS for appending : $! ...\n";

########################

#read values in condor.input
open(IN, "$_CONDOR_INPUT") or die "Can't open $_CONDOR_INPUT file for reading ...\n";
print STDOUT "reading values in $_CONDOR_INPUT ... ";
my $weights=<IN>; chomp $weights;
close(IN);
print STDOUT " done !\n";
print STEPS "$weights, ";
my @_WEIGHTS = split(/\s+/, $weights);

my @_COSTS=("--deletion", $_WEIGHTS[0]);
push(@_COSTS, ("--stem", $_WEIGHTS[1]));
push(@_COSTS, ("--synonym", $_WEIGHTS[2]));
push(@_COSTS, ("--insertion", $_WEIGHTS[3]));
push(@_COSTS, ("--substitution", $_WEIGHTS[4]));
if(scalar @_WEIGHTS > 6)
{
    push(@_COSTS, ("--match", $_WEIGHTS[5]));
    push(@_COSTS, ("--shift", $_WEIGHTS[6]));
    print STDOUT " optimizing 7 weights -> MATCH COST = $_WEIGHTS[5]\n";
}
else
{
    push(@_COSTS, ("--match", 0.0));
    push(@_COSTS, ("--shift", $_WEIGHTS[5]));
    print STDOUT " optimizing 6 weights -> MATCH COST set at 0.0\n";
}

if($_SHIFT_CONSTRAINT eq "relax")
{
	my $tool_dir="/lium/buster1/barrault/TOOLS/";
    my $terp_dir="$tool_dir/terp/terp.v1/data";
	$_WORDNET="$tool_dir/WordNet-3.0/dict/" unless defined $_WORDNET;
    die "Wordnet directory not found: $_WORDNET" unless (-e $_WORDNET);
	$_PARAPHRASE_DB="$terp_dir/phrases.db" unless defined $_PARAPHRASE_DB;
    die "Paraphrase DB not found: $_PARAPHRASE_DB" unless (-e $_PARAPHRASE_DB);
	$_SHIFT_STOP_WORD_LIST="$terp_dir/shift_word_stop_list.txt" unless defined $_SHIFT_STOP_WORD_LIST;
    die "Shift-stop-word-list not found: $_SHIFT_STOP_WORD_LIST" unless (-e $_SHIFT_STOP_WORD_LIST);
}

my $_NB_SYS=@_HYPOTHESES;
die "Please, specify at least two CNs for system combination ...\n" unless($_NB_SYS >= 2);
die "Please, specify a working directory ...\n" unless(defined($_WORKING_DIR) && ($_WORKING_DIR ne "")) ;


########################

my @cmd = ("time", "$_MANY_BLEU", "--many", $_MANY, "--working-dir", "$_WORKING_DIR", "--output", $_OUTPUT); #, "--reference", $_REFERENCE);
foreach my $r (@_REFERENCES)
{
    push(@cmd, ("--reference", "$r"));
}

foreach my $h (@_HYPOTHESES)
{
    push(@cmd, ("--hyp", "$h"));
}
push(@cmd, ("--nb-backbones", $_NB_BACKBONES));
push(@cmd, @_COSTS);
push(@cmd, ("--shift-constraint", $_SHIFT_CONSTRAINT));
push(@cmd, ("--wordnet", $_WORDNET));
push(@cmd, ("--paraphrases", $_PARAPHRASE_DB));
push(@cmd, ("--shift-stop-word-list", $_SHIFT_STOP_WORD_LIST));
push(@cmd, ("--multithread", $_NB_THREADS));
push(@cmd, ("--priors",  @_PRIORS)) if(scalar @_PRIORS > 0);
push(@cmd, ("--log-base", $_LOG_BASE));
   
print STDOUT "Executing <MANYbleu>: ".join(" ", @cmd)."\n";

safesystem(@cmd);


########################

my $output = $_OUTPUT;
$output = $_WORKING_DIR/$output unless ($output =~ /^\//);

die "Output file $output not found! " unless (-e "$output");
cp("$output","$output.$_ITER");

# Make sum of BLEU score
print STDOUT "Starting final scoring with BLEU_RECALL ...";

open(OUTI, "$output.$_ITER") or die "Can't open output file $output.$_ITER\n"; 
my $score=0.0;
while(<OUTI>)
{
    chomp;
    @_ = split(/\s+/, $_);
    $score += $_[1];
}
close(OUTI);

open(OUTPUT, ">$_CONDOR_OUTPUT") or die "Can't create file $_CONDOR_OUTPUT : $!...\n";
printf OUTPUT "A\n1\n%f\n", -$score;
printf STEPS "%f\n", -$score;
print STDOUT " done !";
close(STEPS);
close(OUTPUT);

if($_ITER == 0)
{
    print STDOUT "First run : saving CNs for default values";
    for(my $i=0; $i<$_NB_SYS; $i++)
    {
        cp("$output.cn.$i", "$_WORKING_DIR/DEFAULT.".basename($output).".cn.$i");
        cp("$output.cn.$i", "$_WORKING_DIR/BEST.".basename($output).".cn.$i");
    }
    
    open(BESTWEIGHTS, ">$_CONDOR_BESTWEIGHTS") or die "Can't create $_CONDOR_BESTWEIGHTS file! $!";
    print BESTWEIGHTS "deletion:$_WEIGHTS[0] stem:$_WEIGHTS[1] synonym:$_WEIGHTS[2] insertion:$_WEIGHTS[3] substitution:$_WEIGHTS[4]";
    if(scalar @_WEIGHTS > 6)
    {
        print BESTWEIGHTS " match:$_WEIGHTS[5] shift:$_WEIGHTS[6]";
    }
    else
    {
        print BESTWEIGHTS " match:0.0 shift:$_WEIGHTS[5]";
    }
    close(BESTWEIGHTS);
}
else
{
    my $best_score;
    my $update_best = 1;
    if(-e $_CONDOR_BEST)
    {
        open(BEST, "<$_CONDOR_BEST") or die "Can't create $_CONDOR_BEST file! $!";
        $best_score = <BEST>;
        $update_best = 0 unless($score > $best_score);
        close(BEST);
    }
    if($update_best)
    {
        print STDOUT "Best run : saving CNs for tuned values";
        unlink glob "BEST.$_OUTPUT.cn.*";
        for(my $i=0; $i<$_NB_SYS; $i++)
        {
            cp("$output.cn.$i", "$_WORKING_DIR/BEST.".basename($output).".cn.$i");
        }

        open(BESTWEIGHTS, ">$_CONDOR_BESTWEIGHTS") or die "Can't create $_CONDOR_BESTWEIGHTS file! $!";
        print BESTWEIGHTS "deletion:$_WEIGHTS[0] stem:$_WEIGHTS[1] synonym:$_WEIGHTS[2] insertion:$_WEIGHTS[3] substitution:$_WEIGHTS[4]";
        if(scalar @_WEIGHTS > 6)
        {
            print BESTWEIGHTS " match:$_WEIGHTS[5] shift:$_WEIGHTS[6]";
        }
        else
        {
            print BESTWEIGHTS " match:0.0 shift:$_WEIGHTS[5]";
        }
        close(BESTWEIGHTS);
    }
    else
    {
        print STDOUT "Worse run : score=$score and best_score=$best_score\n";
    }
}


my $nextiter=$_ITER+1;
open(ITER, ">$_CONDOR_ITERATION") or die "Can't write to file $_CONDOR_ITERATION\n";
print ITER "$nextiter"; 
close(ITER);
print STDOUT " ******** -----                 ------- ********";


print STDOUT "Optimize_MANYbleu.pl - end time is: ";
`date`;



######################## FUNCTIONS

sub safesystem {
  print STDERR "Executing: @_\n";
  system(@_);
  if ($? == -1) {
      print STDERR "Failed to execute: @_\n  $!\n";
      exit(1);
  }
  elsif ($? & 127) {
      printf STDERR "Execution of: @_\n  died with signal %d, %s coredump\n",
          ($? & 127),  ($? & 128) ? 'with' : 'without';
      exit(1);
  }
  else {
    my $exitcode = $? >> 8;
    print STDERR "Exit code: $exitcode\n" if $exitcode;
    return ! $exitcode;
  }
}


