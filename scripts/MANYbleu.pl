#!/usr/bin/perl
##!/opt/local/bin/perl

use strict;
#use warnings;
use File::Path;
use File::Basename;
use Cwd;
use Getopt::Long;
use File::Copy "cp";

########### VARIABLES DEFINITION

# Tools
my $_MANY_DIR="$ENV{MANY_HOME}";
my $_MANY="$_MANY_DIR/lib/MANY.jar";

my $create_MANY_config="$_MANY_DIR/scripts/create_many_config.pl";

# General parameters
my $_MANY_CONFIG="many.config.xml";
my $_OUTPUT=undef;
my $_CURRENT_DIR=cwd();
my $_WORKING_DIR=undef;
my $_CONFIG_FILE=undef;
my @_HYPOTHESES=();
my @_HYPOTHESES_ID=();
my $_NB_SYS=undef;
my $_NB_THREADS=undef;
my @_REFERENCES=();
my $_NB_BACKBONES=-1;

# TERp parameters
my ($_DEL_COST, $_STEM_COST, $_SYN_COST, $_INS_COST, $_SUB_COST, $_MATCH_COST, $_SHIFT_COST) = (1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 1.0);
my $_SHIFT_CONSTRAINT = "relax";

my ($_WORDNET, $_PARAPHRASE_DB, $_SHIFT_STOP_WORD_LIST) = (undef, undef, undef);
my $_TERP_PARAMS="terp.params";
my @_PRIORS=();
my $_LOG_BASE=undef;

#Others
my $_CLEAN=1;
my $_HELP;

my %_CONFIG = (	
'many' => \$_MANY, 
'config' => \$_CONFIG_FILE, 
'working-dir' => \$_WORKING_DIR,
'output' => \$_OUTPUT, 
'hyp' => \@_HYPOTHESES, 
'nb-backbones' => \$_NB_BACKBONES, 
'reference' => \@_REFERENCES,
'deletion' => \$_DEL_COST, 'stem' => \$_STEM_COST,
'synonym' => \$_SYN_COST, 'insertion' => \$_INS_COST,
'substitution' => \$_SUB_COST, 'match' => \$_MATCH_COST,
'shift' => \$_SHIFT_COST,
'wordnet' => \$_WORDNET, 'shift-stop-word-list' => \$_SHIFT_STOP_WORD_LIST,
'paraphrases' => \$_PARAPHRASE_DB, 'shift-constraint' => \$_SHIFT_CONSTRAINT,
'multithread' => \$_NB_THREADS,
'priors' => \@_PRIORS, 
'logBase' => \$_LOG_BASE, 'log-base' => \$_LOG_BASE, 
'help' => \$_HELP
); 

########################
######################## FUNCTIONS DEFINITION
my $usage = "MANYdecode.pl \
--many <MANY.jar to use>             : default is $_MANY
--config <config file>               : use parameter values in config. Each parameter value can be overriden by using the corresponding switch.
--working-dir <working directory>    : \
--output <output file>               : \
--hyp <hypothesis file>              : repeat this param for each input hypotheses \
--nb-backbones <number of backbones> : default is -1 meaning every system is considered as backbone \
--reference <reference file> \
**** TERp PARAMETERS \
--deletion <deletion cost>           : default is $_DEL_COST \
--stem <stem cost>                   : default is $_STEM_COST \
--synonym <synonym cost>             : default is $_SYN_COST \
--insertion <insertion cost>         : default is $_INS_COST \
--substitution <substitution cost>   : default is $_SUB_COST \
--match <match cost>                 : default is $_MATCH_COST \
--shift <shift cost>                 : default is $_SHIFT_COST \
--wordnet <wordnet database> 	     : path to WordNet database \
--shift-stop-word-list <list> 	     : path to TERp shift stop word list \
--paraphrases <paraphrase db> 	     : path to TERp paraphrase table \
--shift-constraint <constraint>      : default is $_SHIFT_CONSTRAINT, possible values are exact or relax \
--multithread <number of threads> \
--priors <systems priors> \
--log-base <base for log> \

--help                               : print this help and exit \n";

########################
######################## Parsing parameters with GetOptions
$_HELP = 1 unless GetOptions(\%_CONFIG, 'many=s', 'config=s', 'working-dir=s', 'output=s', 'hyp=s@', 'nb-backbones=i', 'reference=s@',
'deletion=f', 'insertion=f', 'substitution=f', 'shift=f', 'stem=f', 'synonym=f', 'match=f',
'wordnet=s', 'shift-stop-word-list=s', 'paraphrases=s', 'shift-constraint=s',
'multithread=i', 'priors=f{,}', 
'log-base=f', 'help');

######################## PREPARE DATA

die $usage if ($_HELP);

print "$0 running on ".`hostname -f`."\n";

if(defined $_CONFIG_FILE)
{
	print STDOUT "loading config file : $_CONFIG_FILE\n";
	scan_many_config($_CONFIG_FILE);
	#dump_config(%_CONFIG);
}

die "$_MANY not found!\n" if(! -e $_MANY);
$_NB_SYS = @_HYPOTHESES;
die "Please, specify at least two hypotheses for system combination ...\n" if($_NB_SYS < 2);
die "Please, specify a working directory ...\n" if !defined($_WORKING_DIR);

my @_COSTS=("--deletion", $_DEL_COST, "--stem", $_STEM_COST, "--synonym", $_SYN_COST, "--insertion", $_INS_COST, "--substitution", $_SUB_COST, "--match", $_MATCH_COST, "--shift", $_SHIFT_COST);

if(@_PRIORS == 0)
{
	print STDOUT ("*********\n**** Warning: no priors given, using 0.1 for each system ****\n*********\n");
	foreach my $h (@_HYPOTHESES)
	{
		push(@_PRIORS, 0.1);
	}
}

if($_SHIFT_CONSTRAINT eq "relax")
{
	my $tool_dir="/lium/buster1/barrault/TOOLS/";
    my $terp_dir="$tool_dir/terp/terp.v1/data";
	#my $terp_dir="/Users/barrault/Documents/TERp-Ressources";
	$_WORDNET="$tool_dir/WordNet-3.0/dict/" unless defined $_WORDNET;
	#$_WORDNET="/usr/local/WordNet-3.0/dict/";
    die "Wordnet directory not found: $_WORDNET" unless (-e $_WORDNET);
	$_PARAPHRASE_DB="$terp_dir/phrases.db" unless defined $_PARAPHRASE_DB;
	#$_PARAPHRASE_DB="$terp_dir/sample.pt.db";
    die "Paraphrase DB not found: $_PARAPHRASE_DB" unless (-e $_PARAPHRASE_DB);
	$_SHIFT_STOP_WORD_LIST="$terp_dir/shift_word_stop_list.txt" unless defined $_SHIFT_STOP_WORD_LIST;
    die "Shift-stop-word-list not found: $_SHIFT_STOP_WORD_LIST" unless (-e $_SHIFT_STOP_WORD_LIST);
}

die $usage if ($_HELP);


#dump_vars();

print STDOUT "MANYbleu.pl - start time is: ".`date`."\n";

print STDOUT "Creating working directory : $_WORKING_DIR ...";
mkpath $_WORKING_DIR;
print STDOUT " OK \n";

print STDOUT "Entering $_WORKING_DIR ... \n";
chdir $_WORKING_DIR;

my $_DATA_DIR="$_WORKING_DIR/data";

print STDOUT "Using $_NB_SYS systems ...\n";
print STDOUT "Checking size of hypotheses files ...\n";
my $nb_sent = undef;

#print "HYPS BEFORE : @_HYPOTHESES\n";
my @_HYPS_ABS = (); # required, input texts to combine (absolute)
foreach my $input (@_HYPOTHESES)
{
	my $input_abs = ensure_full_path($input);
    die "$input not found (interpreted as $input_abs)." unless (-e $input_abs);
    push(@_HYPS_ABS, $input_abs);
    if(!defined $nb_sent)
    { 
        open(F0, "<$input_abs") or die("Can't open $input_abs"); 
        my @lines = <F0>;
        close(F0);
        $nb_sent = @lines;
        die "No sentence to process ...\n" if($nb_sent==0);
        print STDOUT "$nb_sent sentences to process ...\n";
    }
    else
    {
        # Sanity check of hypotheses
	    open(FH, "<$input_abs");
	    my @lines = <FH>;
	    close(FH);
	    die "\nERROR : Hypotheses files $_HYPOTHESES[0] and $input have not the same size ... exiting!" if(@lines != $nb_sent);
    }
}
@_HYPOTHESES = @_HYPS_ABS;
#print "HYPS AFTER : @_HYPOTHESES\n";
print STDOUT "hypotheses files: OK \n";


# Cleaning working dir
if($_CLEAN > 0)
{
	print STDOUT "Cleaning working directory $_WORKING_DIR...";
	unlink $_OUTPUT;
	print STDOUT " OK \n";
}


=begin
######################## GENERATE SCORES AND ID FILES
# - This will be changed later when better weights will be calculated

for(my $i=0; $i<$_NB_SYS; $i++)
{
    # 11/12/2011 : better to always rewrite .id files
    #if( (-e "${_HYPOTHESES[$i]}.id") && (-e "${_HYPOTHESES[$i]}.sc.id") )
    #{
    #    print STDOUT "${_HYPOTHESES[$i]}.id and ${_HYPOTHESES[$i]}.sc.id already exist ... reusing!\n";
    #    next;
    #}
	
    print STDOUT "Generating score file and adding ids for $_HYPOTHESES[$i]...";
    open(FROM, "$_HYPOTHESES[$i]") or die "Can't open $_HYPOTHESES[$i]\n";
    my @lines = <FROM>;
    close(FROM);
    # 05/03/12 now create hypotheses in data dir
    my $basename = basename($_HYPOTHESES[$i]); 
    open(ID, ">$_DATA_DIR/$basename.id") or die "Cant create $_DATA_DIR/$basename.id\n";
    open(SC_ID, ">$_DATA_DIR/$basename.sc.id") or die "Cant create $_DATA_DIR/$basename.sc.id\n";
	my $nbl = 0;
    foreach my $line (@lines)
    {
        my $sc_line = $line;
        $line =~ s/$/ ([set][doc.00][$nbl])/;	
		print ID $line;
        $sc_line =~ s/[^ \n]+/$_PRIORS[$i]/g;
        $sc_line =~ s/$/ ([set][doc.00][$nbl])/;	
		print SC_ID $sc_line;
		$nbl++;
    }
    close(ID);
    close(SC_ID);

    push(@_HYPOTHESES_ID, "$_DATA_DIR/$basename");

    print STDOUT " OK \n";
}

foreach my $ref (@_REFERENCES)
{
    print STDOUT "Adding ids for $ref...";
    open(FROM, "$ref") or die "Can't open reference file $ref. $!\n";
    my @lines = <FROM>; 
    close(FROM);
    my $basename = basename($ref); 
    open(ID, ">$_DATA_DIR/$basename.id") or die "Cant create reference.id file $_DATA_DIR/$basename.id. $!\n";
    my $nbl=0;
    foreach my $line (@lines)
    {
        $line =~ s/$/ ([set][doc.00][$nbl])/;
        print ID $line;
        $nbl++;
    }
    close(ID);

    push(@_REFERENCES_ID, "$_DATA_DIR/$basename");
    
    print STDOUT " OK \n";
}
=cut
######################## GENERATE MANY CONFIG FILE
## Build command

# The arguments for generate_config function
my @cmd =("$create_MANY_config");
#push(@cmd, ("--config-type", "BLEU", "--nbsys", "$_NB_SYS", "--output", "$_OUTPUT"));
push(@cmd, ("--config-type", "BLEU", "--output", "$_OUTPUT"));
#push(@cmd, ("--reference", $_REF_BASENAME));
foreach my $r (@_REFERENCES)
{
    push(@cmd, ("--reference", $r));
}
foreach my $f (@_HYPOTHESES)
{
    push(@cmd, ("--hyp", $f));
}
push(@cmd, ("--nb-backbones", $_NB_BACKBONES));

push(@cmd, @_COSTS);
push(@cmd, ("--shift-constraint", $_SHIFT_CONSTRAINT));
print STDOUT "shift constraint : $_SHIFT_CONSTRAINT\n";

if($_SHIFT_CONSTRAINT eq "relax")
{
    push(@cmd, ("--paraphrases", $_PARAPHRASE_DB)) if(defined $_PARAPHRASE_DB);
    push(@cmd, ("--shift-stop-word-list", $_SHIFT_STOP_WORD_LIST));
    push(@cmd, ("--wordnet", $_WORDNET)) if defined($_WORDNET);
}
push(@cmd, ("--priors", @_PRIORS));

push(@cmd, ("--multithread", $_NB_THREADS)) if(defined($_NB_THREADS));

print STDERR "------------------------------\nMANYbleu CONFIG: ";
print STDERR join(" ", @cmd);
print STDERR "\n------------------------------\n";

#create_MANY_config(@cmd);
safesystem(@cmd);
print STDOUT " done !\n";



######################## CALL MANY

my $enc="UTF-8";
#my $enc="ISO_8859_1";

print STDOUT "CMD : java -Xmx10G -Dfile.encoding=".$enc." -cp $_MANY edu.lium.mt.MANYbleu $_MANY_CONFIG\n";
print STDOUT "Starting MANY system combination ...";
print STDOUT "\n------- START LOG ----\n";
safesystem("java -Xmx30G -Dfile.encoding=$enc -cp $_MANY edu.lium.mt.MANYbleu $_MANY_CONFIG");
print STDOUT "\n------- END LOG ----\n";
print STDOUT " OK \n";

chdir "./..";

print STDOUT "MANYbleu.pl - end time is: ";
`date`;

######################## FUNCTIONS

sub checkType($)
{
    my $ret = 0;
    $ret = 1 if $_[0] eq "MANY";   # MANY config file
    $ret = 1 if $_[0] eq "DECODE"; # MANYdecode config file
    $ret = 1 if $_[0] eq "BLEU";   # MANYbleu config file
    return $ret;
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

sub scan_many_config()
{
	my $xml = shift;
  	my $xmlshortname = $xml; $xmlshortname =~ s/^.*\///; 
  	open XML, $xml or die "Can't read $xml";
  	my $nr = 0;
  	my $error = 0;
	while (<XML>) 
	{
		$nr++;
		chomp;
		next if /^\s*#/; # skip comments
		next if /^\s*$/; # skip blank lines
		
		if(/^<property name=\"(.+)\"\s+value=\"(.+)\"/) {
			my $param_name = $1;
			my $param_val = $2;
			
			$param_val =~ s/file:// if ($param_name eq "dictionaryPath"); 
			
			if(exists($_CONFIG{$param_name}))
			{
				if(($param_name ne "priors") && ($param_name ne "hypotheses-cn") && ($param_name ne "hypotheses"))
				{
					if(!defined(${$_CONFIG{$param_name}}))
					{
				   		${$_CONFIG{$param_name}} = $param_val;
				   	}
				   	#else{ print "$param_name already defined to val >${$_CONFIG{$param_name}}<\n"; }
				}
				else
				{
				    if(!defined(@{$_CONFIG{$param_name}}))
				    {
				    	push(@{$_CONFIG{$param_name}}, split(/\s+/, $param_val));
				    }
				    #else { print "$param_name already defined to val >$_CONFIG{$param_name}<\n"; }
				}
			}
		}
	}
	die "$xmlshortname: File was empty!" if !$nr;
	close XML;
}

sub dump_config {
  print STDOUT "------ CONFIG -------\n";
  my %config = @_;
  foreach my $name (keys %config) 
  {
     if(($name ne "priors") && ($name ne "hypotheses-cn"))
     {	
     	print STDOUT "PARAM: $name\tVAL: ${$config{$name}}\n";
     }
     else
     {
     	print STDOUT "PARAM: $name\n";
     	my $i=0; 
     	foreach my $v (@{$config{$name}})
     	{
     		print STDOUT "\tVAL[$i]: $v\n";
     		$i++
     	}
     }
  }
  print STDOUT "------ CONFIG -------\n";
}

sub dump_vars()
{
print STDOUT "------ VARS -------\n";
print STDOUT " MANY_DIR: $_MANY_DIR\n";
print STDOUT " MANY: $_MANY\n";

print STDOUT " MANY_CONFIG: $_MANY_CONFIG\n";
print STDOUT " OUTPUT: $_OUTPUT\n";
print STDOUT " CURRENT_DIR: $_CURRENT_DIR\n";
print STDOUT " WORKING_DIR: $_WORKING_DIR\n";
print STDOUT " CONFIG_FILE: $_CONFIG_FILE\n";
print STDOUT " HYPOTHESES: @_HYPOTHESES\n";
print STDOUT " NB_SYS: $_NB_SYS\n";
print STDOUT " NB_THREADS: $_NB_THREADS\n";


print STDOUT " INS_COST: $_INS_COST\n";
print STDOUT " DEL_COST: $_DEL_COST\n";
print STDOUT " SUB_COST: $_SUB_COST\n";
print STDOUT " SHIFT_COST: $_SHIFT_COST\n";
print STDOUT " STEM_COST: $_STEM_COST\n";
print STDOUT " SYN_COST: $_SYN_COST\n";
print STDOUT " MATCH_COST: $_MATCH_COST\n";

print STDOUT " SHIFT_CONSTRAINT: $_SHIFT_CONSTRAINT\n";
print STDOUT " WORDNET: $_WORDNET\n";
print STDOUT " SHIFT_STOP_WORD_LIST: $_SHIFT_STOP_WORD_LIST\n";
print STDOUT " PARAPHRASES: $_PARAPHRASE_DB\n";

print STDOUT " PRIORS: @_PRIORS\n";
print STDOUT " LOG_BASE: $_LOG_BASE\n";
print STDOUT "------ END VARS -------\n";

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

