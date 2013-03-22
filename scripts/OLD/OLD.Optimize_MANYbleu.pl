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
use Getopt::Long;
use Cwd;

########### VARIABLES DEFINITION

# Tools
my $_BIN_DIR="$ENV{HOME}/RELEASE/bin";
#my $_MANY="$_BIN_DIR/MANY_425.jar";
my $_MANY="$_BIN_DIR/MANY_501.jar";
my $_MANY_DIR="/lium/buster1/barrault/SRC/MANY";
my $_MANY_BLEU="$_MANY_DIR/scripts/MANYbleu.pl";

# Files
my $_MANY_CONFIG="many.config.xml";
my $_OUTPUT="many.output";
my $_CURRENT_DIR=cwd();
my $_WORKING_DIR="syscomb_manybleu";
my @_PRIORS=();
my $_DATA_DIR="";
my @_HYPOTHESES=qw( toto tata ); 
my $_NB_SYS=@_HYPOTHESES;
foreach my $h (@_HYPOTHESES)
{
    push(@_PRIORS, 1.0/$_NB_SYS);
}
my $_REFERENCE="$_DATA_DIR/reference";

#0ther variables
my $_NB_THREADS=5;
my $_LOG_BASE=2.718;

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

########################

print STDOUT "    MANY - Open Source Machine Translation System Combination\n";

########################

die "Please, specify at least two 1-best outputs for system combination ...\n" if($_NB_SYS < 2);

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
my $weights=<IN>;
close(IN);
print STDOUT " done !\n";
my @_WEIGHTS = split(/\s+/, $weights);

my @_COSTS=("--deletion", $_WEIGHTS[0]);
push(@_COSTS, ("--stem", $_WEIGHTS[1]));
push(@_COSTS, ("--synonym", $_WEIGHTS[2]));
push(@_COSTS, ("--insertion", $_WEIGHTS[3]));
push(@_COSTS, ("--substitution", $_WEIGHTS[4]));
push(@_COSTS, ("--match", 0.0));
push(@_COSTS, ("--shift", $_WEIGHTS[5]));

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

die "Please, specify at least two CN for system combination ...\n" if($_NB_SYS < 2);
die "Please, specify a working directory ...\n" if !defined($_WORKING_DIR);

########################

my @cmd = ("time", "$_MANY_BLEU", "--many", $_MANY, "--working-dir", "$_WORKING_DIR", "--output", $_OUTPUT, "--reference", $_REFERENCE);
foreach my $h (@_HYPOTHESES)
{
    push(@cmd, ("--hyp", "$_DATA_DIR/$h"));
}
push(@cmd, @_COSTS);
push(@cmd, ("--priors",  @_PRIORS));
push(@cmd, ("--shift-constraint", $_SHIFT_CONSTRAINT));
push(@cmd, ("--wordnet", $_WORDNET));
push(@cmd, ("--paraphrases", $_PARAPHRASE_DB));
push(@cmd, ("--shift-stop-word-list", $_SHIFT_STOP_WORD_LIST));
push(@cmd, ("--log-base", $_LOG_BASE));
   
print STDOUT "Executing ".join(" ", @cmd);

safesystem(@cmd);


########################


cp("$_WORKING_DIR/$_OUTPUT","$_WORKING_DIR/$_OUTPUT.$_ITER");

# Make sum of BLEU score
print STDOUT "Starting final scoring with BLEU_RECALL ...";

open(OUTI, "$_WORKING_DIR/$_OUTPUT.$_ITER") or die "Can't open output file $_WORKING_DIR/$_OUTPUT.$_ITER\n"; 
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

my $nextiter=$_ITER+1;
open(ITER, ">$_CONDOR_ITERATION") or die "Can't write to file $_CONDOR_ITERATION\n";
print ITER "$nextiter"; 
close(ITER);
print STDOUT " ******** -----                 ------- ********";


print STDOUT "Optimize_MANY.pl - end time is: ";
`date`;



######################## FUNCTIONS
sub add_id()
{
	my $file = $_[0];
	open(FROM, "$file") or die "Cant open file $!\n";
    my @lines = <FROM>;
    close(FROM);
	open(TO, ">$file.id") or die "Cant create file $!\n";
	my $nbl = 0;
	foreach my $line (@lines)
	{
		$line =~ s/$/([set][doc.00][$nbl])/;	
		$nbl++;
		print TO $line;
	}
	close(TO);
}

sub checkType($)
{
    my $ret = 0;
    $ret = 1 if $_[0] eq "MANY";   # MANY config file
    $ret = 1 if $_[0] eq "DECODE"; # MANYdecode config file
    $ret = 1 if $_[0] eq "BLEU";   # MANYbleu config file
    return $ret;
}

sub create_MANY_config()
{
    local @ARGV = @_;

open(MANYCFG, '>', $_MANY_CONFIG) or die "Can't create file $_MANY_CONFIG : $!"; 

my ($_CONFIG_TYPE, $_OUTPUT, $_REFERENCE,
$_LM_WEIGHT, $_NULL_PEN, $_WORD_PEN,
$_DEL_COST, $_STEM_COST, $_SYN_COST, $_INS_COST, $_SUB_COST, $_MATCH_COST, $_SHIFT_COST, 
$_MAX_NB_TOKENS, $_NBEST_SIZE, $_NBEST_FORMAT, $_NBEST_FILE,
$_USE_CN, $_USE_WORDNET, $_USE_PARAPHRASE_DB, $_USE_STEMS, $_CASE_SENSITIVE,
$_WORDNET, $_SHIFT_STOP_WORD_LIST, $_PARAPHRASE_DB,
$_SHIFT_CONSTRAINT, $_TERP_PARAMS,
$_LMSERVER_HOST, $_LMSERVER_PORT, $_LM, $_LM_ORDER, $_VOCAB,
$_NB_SYS, $_NB_THREADS,
$_LOG_BASE,
$_DEBUG_DECODE,
$_HELP
) = (
"MANY", "output.many", undef,
0.1, 0.3, 0.1,
1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 1.0,
5000, 0, "MOSES", "nbest.txt",
"true", undef, undef, "true", "true",
undef,
undef,
undef,
"relax", "terp.params",
"localhost", 1234, undef, 4, undef,
0, undef,
2.718, #natural log;  this was 1.0001 for Sphinx
undef, 
0
);

my @_SYS_PRIORS = ();
my @_HYPOTHESES = ();

my $usage = "create_MANY_config \
--config-type <config type>            : default is $_CONFIG_TYPE, possible values : MANY, DECODE, BLEU\
--output <output filename>             : default is $_OUTPUT \
--nbsys <number of systems>            : default is $_NB_SYS \
--hyp <hypothesis file>	               : repeat this param for each input hypotheses \
--reference <reference file>           : useful for computing MANYbleu \
**** TERP PARAMETERS : \
--deletion <deletion cost>             : default is $_DEL_COST \
--stem <stem cost>                     : default is $_STEM_COST \
--synonym <synonym cost>               : default is $_SYN_COST \
--insertion <insertion cost>           : default is $_INS_COST \
--substitution <substitution cost>     : default is $_SUB_COST \
--match <match cost>                   : default is $_MATCH_COST \
--shift <shift cost>                   : default is $_SHIFT_COST \
--wordnet <wordnet database>  \ 
--shift-stop-word-list <list> \
--paraphrase <paraphrase db>  \
--shift-constraint <constraint>        : default is $_SHIFT_CONSTRAINT, possible values are exact or relax \
--terp-params <TERp params file>       : default is $_TERP_PARAMS \
**** LM PARAMETERS : \
--lm-server-host <LM server host>      : default is $_LMSERVER_HOST \
--lm-server-port <LM srever port>      : default is $_LMSERVER_PORT \
--lm <ngram LM in DMP32 format>  \
--lm-order <LM order>                  : default is $_LM_ORDER \
--vocab <vocab file> \
**** DECODER PARAMETERS : \
--lm-weight <lm weight>                : default is $_LM_WEIGHT \
--null-penalty <null penalty>          : default is $_NULL_PEN \
--word-penalty <word penalty>          : default is $_WORD_PEN \
--multithread <number of thread> \
--priors <systems priors>              : default is @_SYS_PRIORS \
--max-nb-tokens <max number of tokens> : default is $_MAX_NB_TOKENS \
--nbest-size <size of the nbest list>  : default is $_NBEST_SIZE \
--nbest-format <format of the nbest list>  : default is $_NBEST_FORMAT, possible values are MOSES or BTEC \
--nbest-file <filename for the nbest list> : default is $_NBEST_FILE \
--log-base <base for log>              : default is $_LOG_BASE \
--debug-decode \
--help                                 : print this help and exit \
";

$_HELP = 1
    unless GetOptions('config-type=s' => \$_CONFIG_TYPE,
                       'output=s' => \$_OUTPUT,
                       'nbsys=i' => \$_NB_SYS,
                       'hyp=s' => \@_HYPOTHESES,
                       'reference=s' => \$_REFERENCE,
                       'deletion=f' => \$_DEL_COST,
                       'stem=f' => \$_STEM_COST,
                       'synonym=f' => \$_SYN_COST,
                       'insertion=f' => \$_INS_COST,
                       'substitution=f' => \$_SUB_COST,
                       'match=f' => \$_MATCH_COST,
                       'shift=f' => \$_SHIFT_COST,
                       'wordnet=s' => \$_WORDNET,
                       'shift-stop-word-list=s' => \$_SHIFT_STOP_WORD_LIST,
                       'paraphrase-db=s' => \$_PARAPHRASE_DB,
                       'shift-constraint=s' => \$_SHIFT_CONSTRAINT,
                       'terp-params=s' => \$_TERP_PARAMS,
                       'lm-server-host=s' => \$_LMSERVER_HOST,
                       'lm-server-port=i' => \$_LMSERVER_PORT,
                       'lm=s' => \$_LM,
                       'lm-order=i' => \$_LM_ORDER,
                       'lm-weight=f' => \$_LM_WEIGHT,
                       'vocab=s' => \$_VOCAB,
                       'null-penalty=f' => \$_NULL_PEN,
                       'word-penalty=f' => \$_WORD_PEN,
                       'multithread=i' => \$_NB_THREADS,
                       'priors=f{,}' => \@_SYS_PRIORS,
                       'max-nb-tokens=i' => \$_MAX_NB_TOKENS,
                       'nbest-size=i' => \$_NBEST_SIZE,
                       'nbest-format=s' => \$_NBEST_FORMAT,
                       'nbest-file=s' => \$_NBEST_FILE,
                       'log-base=f' => \$_LOG_BASE,
                       'debug-decode' => \$_DEBUG_DECODE,
                       'help' => \$_HELP
                      );


if(checkType($_CONFIG_TYPE) == 0)
{
    print "Bad config type '$_CONFIG_TYPE', should be one of MANY, DECODE, BLEU)\n";
    $_HELP = 1;
}
if(@_HYPOTHESES < 2)
{
	print "Number of systems must be greater than 1 !\n";
    $_HELP = 1;
}
if(@_HYPOTHESES != @_SYS_PRIORS)
{
	print "Number of systems (".scalar @_HYPOTHESES.") is different from number of priors (".scalar @_SYS_PRIORS.") !\n";
	$_HELP = 1;
}

if (($_CONFIG_TYPE eq "MANY") || ($_CONFIG_TYPE eq "DECODE"))
{
    if(!defined $_VOCAB)
    {
        print "Please, specify a vocabulary file ... exiting!";
        $_HELP=1;
    }
    elsif(! -e $_VOCAB)
    {
        print "$_VOCAB not found ... exiting!";
        $_HELP=1;
    }
}

$_USE_WORDNET = "true" if defined($_WORDNET);
$_USE_PARAPHRASE_DB = "true" if defined($_PARAPHRASE_DB);

if ($_HELP) {
    print $usage;
    exit;
}

print MANYCFG '<?xml version="1.0" encoding="UTF-8"?>'."\n";
print MANYCFG '<config>'."\n";
print MANYCFG "\n";
print MANYCFG '<property name="logLevel"     value="FINE"/>'."\n";
print MANYCFG '<property name="showCreations"     value="true"/>'."\n";
    
print MANYCFG "\n";

print MANYCFG '<component  name="decoder" type="edu.lium.decoder.TokenPassDecoder">'."\n";
print MANYCFG '<property name="dictionary" value="dictionary"/>'."\n";
print MANYCFG '<property name="logMath" value="logMath"/>'."\n";
print MANYCFG '<property name="useNGramModel" value="';
defined($_LM) ? print MANYCFG "true": print MANYCFG "false";
print MANYCFG '"/>'."\n";
print MANYCFG '<property name="logLevel"     value="FINE"/>'."\n";
print MANYCFG '<property name="lmonserver" value="lmonserver"/>'."\n";
print MANYCFG '<property name="ngramModel" value="ngramModel"/>'."\n";
print MANYCFG '<property name="lm-weight" value="'.$_LM_WEIGHT.'"/>'."\n"; #." (This value is multiplied by 10 in the software) \n";
print MANYCFG '<property name="null-penalty" value="'.$_NULL_PEN.'"/>'."\n";
print MANYCFG '<property name="word-penalty" value="'.$_WORD_PEN.'"/>'."\n"; #." (This value is multiplied by 10 in the software)\n";
print MANYCFG '<property name="max-nb-tokens" value="'.$_MAX_NB_TOKENS.'"/>'."\n";
print MANYCFG '<property name="nbest-size" value="'.$_NBEST_SIZE.'"/>'."\n";
print MANYCFG '<property name="nbest-format" value="'.$_NBEST_FORMAT.'"/>'."\n";
print MANYCFG '<property name="nbest-file" value="'.$_NBEST_FILE.'"/>'."\n";
print MANYCFG '<property name="debug" value="';
defined($_DEBUG_DECODE) ? print MANYCFG "true": print MANYCFG "false";
print MANYCFG '"/>'."\n";
print MANYCFG '</component>'."\n";

print MANYCFG "\n";

#<!-- create the configMonitor  -->
print MANYCFG '<component name="configMonitor" type="edu.cmu.sphinx.instrumentation.ConfigMonitor">'."\n";
print MANYCFG '<property name="showConfig" value="true"/>'."\n";
print MANYCFG '<property name="showConfigAsGDL" value="true"/>'."\n";
print MANYCFG '</component>'."\n";

print MANYCFG "\n";

print MANYCFG '<component name="logMath" type="edu.cmu.sphinx.util.LogMath">'."\n";
print MANYCFG '<property name="logBase" value="'.$_LOG_BASE.'"/>'."\n";
print MANYCFG '<property name="useAddTable" value="false"/>'."\n";
print MANYCFG '</component>'."\n";

print MANYCFG "\n";

if (($_CONFIG_TYPE eq "MANY") || ($_CONFIG_TYPE eq "DECODE"))
{
    print MANYCFG '<component name="dictionary" type="edu.cmu.sphinx.linguist.dictionary.SimpleDictionary">'."\n";
    print MANYCFG '<property name="dictionaryPath" value="file:'.$_VOCAB.'"/>'."\n";
    print MANYCFG '<property name="fillerPath" value=""/>'."\n";
    print MANYCFG '</component>'."\n";

    print MANYCFG "\n";
    
    print MANYCFG '<component  name="lmonserver" type="edu.cmu.sphinx.linguist.language.ngram.NetworkLanguageModel">'."\n";
    print MANYCFG '<property name="port" value="'.$_LMSERVER_PORT.'"/>'."\n";
    print MANYCFG '<property name="host" value="'.$_LMSERVER_HOST.'"/>'."\n";
    print MANYCFG '<property name="maxDepth" value="'.$_LM_ORDER.'"/>'."\n";
    print MANYCFG '<property name="logMath" value="logMath"/>'."\n";
    print MANYCFG '</component>'."\n";

    print MANYCFG "\n";

    if(defined($_LM))
    {
        print MANYCFG '<component name="ngramModel" type="edu.cmu.sphinx.linguist.language.ngram.large.LargeNGramModel">'."\n";
        print MANYCFG '<property name="location" value="'.$_LM.'" />'."\n";
        print MANYCFG '<property name="logMath" value="logMath"/>'."\n";
        print MANYCFG '<property name="dictionary" value="dictionary"/>'."\n";
        print MANYCFG '<property name="maxDepth" value="'.$_LM_ORDER.'"/>'."\n";
        print MANYCFG '<property name="logLevel" value="SEVERE"/>'."\n";
        print MANYCFG '<property name="unigramWeight" value=".7"/>'."\n";
        print MANYCFG '</component>'."\n";
        print MANYCFG "\n";
    }
}
print MANYCFG '<component name="MANY" type="edu.lium.mt.MANY">'."\n" if $_CONFIG_TYPE eq "MANY";
print MANYCFG '<component name="MANYDECODE" type="edu.lium.mt.MANYdecode">'."\n" if $_CONFIG_TYPE eq "DECODE";
print MANYCFG '<component name="MANYBLEU" type="edu.lium.mt.MANYbleu">'."\n" if $_CONFIG_TYPE eq "BLEU";

if($_CONFIG_TYPE eq "MANY" || $_CONFIG_TYPE eq "BLEU")
{
    print MANYCFG '<property name="hypotheses" value="';
    foreach my $f (@_HYPOTHESES)
    {
    	print MANYCFG "$f.id ";
    }
    print MANYCFG '" />'."\n";
    print MANYCFG '<property name="hyps-scores" value="';
    foreach my $f(@_HYPOTHESES)
    {
    	print MANYCFG "$f\.sc.id ";	
    }
    print MANYCFG '" />'."\n";

	print MANYCFG '<property name="insertion"     value="'.$_INS_COST.'"/>'."\n";
	print MANYCFG '<property name="deletion"     value="'.$_DEL_COST.'"/>'."\n";
	print MANYCFG '<property name="substitution"     value="'.$_SUB_COST.'"/>'."\n";
	print MANYCFG '<property name="match"     value="'.$_MATCH_COST.'"/>'."\n";
	print MANYCFG '<property name="shift"     value="'.$_SHIFT_COST.'"/>'."\n";
	print MANYCFG '<property name="stem"     value="'.$_STEM_COST.'"/>'."\n";
	print MANYCFG '<property name="synonym"     value="'.$_SYN_COST.'"/>'."\n";
	
	print MANYCFG '<property name="terpParams"     value="'.$_TERP_PARAMS.'"/>'."\n";
	print MANYCFG '<property name="shift-constraint"     value="'.$_SHIFT_CONSTRAINT.'"/>'."\n";
	print MANYCFG '<property name="wordnet"     value="'.$_WORDNET.'"/>'."\n" if (lc($_SHIFT_CONSTRAINT) eq "relax" && defined($_USE_WORDNET));
	print MANYCFG '<property name="shift-word-stop-list"     value="'.$_SHIFT_STOP_WORD_LIST.'"/>'."\n"  if (lc($_SHIFT_CONSTRAINT) eq "relax" && defined($_SHIFT_STOP_WORD_LIST));
	print MANYCFG '<property name="paraphrases"     value="'.$_PARAPHRASE_DB.'"/>'."\n" if (lc($_SHIFT_CONSTRAINT) eq "relax" && defined($_USE_PARAPHRASE_DB));
	print MANYCFG '<property name="terp" value="terp"/>'."\n";
}
else
{
    print MANYCFG '<property name="hypotheses-cn" value="';
    foreach my $f (@_HYPOTHESES)
    {
	    print MANYCFG "$f ";
    }
    print MANYCFG '" />'."\n";
}

print MANYCFG '<property name="reference" value="'.$_REFERENCE.'.id"/>'."\n" if ($_CONFIG_TYPE eq "BLEU");
print MANYCFG '<property name="decoder" value="decoder"/>'."\n";
print MANYCFG '<property name="output" value="'.$_OUTPUT.'"/>'."\n";
print MANYCFG '<property name="priors" value="'."@_SYS_PRIORS".'"/>'."\n";

print MANYCFG '<property name="multithread"     value="'.$_NB_THREADS.'"/>'."\n" if defined($_NB_THREADS);
print MANYCFG '</component>'."\n";

print MANYCFG '</config>'."\n";

}

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

sub ensure_full_path {
    my $PATH = shift;
	$PATH =~ s/\/nfsmnt//;
    return $PATH if $PATH =~ /^\//;
    my $dir = `pawd 2>/dev/null`; 
    if(!$dir){$dir = `pwd`;}
    chomp($dir);
    $PATH = $dir."/".$PATH;
    $PATH =~ s/[\r\n]//g;
    $PATH =~ s/\/\.\//\//g;
    $PATH =~ s/\/+/\//g;
    my $sanity = 0;
    while($PATH =~ /\/\.\.\// && $sanity++<10) {
        $PATH =~ s/\/+/\//g;
        $PATH =~ s/\/[^\/]+\/\.\.\//\//g;
    }
    $PATH =~ s/\/[^\/]+\/\.\.$//;
    $PATH =~ s/\/+$//;
$PATH =~ s/\/nfsmnt//;
    return $PATH;
}

