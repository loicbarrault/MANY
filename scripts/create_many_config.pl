#!/usr/bin/perl
##!/opt/local/bin/perl

use strict;
use File::Path;
use Cwd;
use Getopt::Long;

########### VARIABLES DEFINITION

# Tools
my $_MANY_DIR="$ENV{HOME}/src/MANY";
my $_MANY="$_MANY_DIR/lib/MANY.jar";

# General parameters
my $_MANY_CONFIG="many.config.xml";
my $_OUTPUT=undef;
my $_CURRENT_DIR=cwd();
my $_WORKING_DIR;
my $_CONFIG_FILE=undef;
my $_CONFIG_TYPE=undef;
my @_HYPOTHESES=();
my $_NB_SYS=undef;
my $_NB_THREADS=undef;
my $_REFERENCE=undef;

# TERp parameters
my ($_DEL_COST, $_STEM_COST, $_SYN_COST, $_INS_COST, $_SUB_COST, $_MATCH_COST, $_SHIFT_COST) = (1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 1.0);
my $_SHIFT_CONSTRAINT = "relax";

my $_TOOLS_DIR="$ENV{HOME}/tools";
my $_WORDNET = "$_TOOLS_DIR/WordNet-3.0/dict/";
my $_SHIFT_STOP_WORD_LIST = "$_TOOLS_DIR/terp/terp.v1/data/shift_word_stop_list.txt";
my $_PARAPHRASE_DB = "$_TOOLS_DIR/terp/terp.v1/data/phrases.db";
my $_TERP_PARAMS="terp.params";

# LM parameters
#my $_LM_SERVER_HOST=`hostname`; chomp $_LM_SERVER_HOST;
my $_LM_SERVER_HOST=undef;
my $_LM_SERVER_PORT=undef;
my $_USE_LOCAL_LM=undef;
my $_LM=undef;
my $_LM_ORDER=undef;
my $_VOCAB=undef;

# Decoder parameters
my @_PRIORS=();
my $_NULL_PEN=undef;
my $_WORD_PEN=undef;
my $_LM_WEIGHT=undef;
my $_MAX_NB_TOKENS=undef;
my $_NBEST_SIZE=undef;
my $_NBEST_FORMAT=undef;
my $_NBEST_FILE=undef;
my $_DEBUG_DECODE=undef;
my $_LOG_BASE=undef;

#Others
my $_CLEAN=1;
my $_HELP;

my %_CONFIG = (	
'many' => \$_MANY, 
'config' => \$_CONFIG_FILE, 
'config-type' => \$_CONFIG_TYPE,
'working-dir' => \$_WORKING_DIR,
'output' => \$_OUTPUT, 
'hyp' => \@_HYPOTHESES, 'hypotheses-cn' => \@_HYPOTHESES, 
'reference' => \$_REFERENCE,
'deletion' => \$_DEL_COST, 'stem' => \$_STEM_COST,
'synonym' => \$_SYN_COST, 'insertion' => \$_INS_COST,
'substitution' => \$_SUB_COST, 'match' => \$_MATCH_COST,
'shift' => \$_SHIFT_COST,
'wordnet' => \$_WORDNET, 'shift-stop-word-list' => \$_SHIFT_STOP_WORD_LIST,
'paraphrases' => \$_PARAPHRASE_DB, 'shift-constraint' => \$_SHIFT_CONSTRAINT,
'lm' => \$_LM,
'lm-server-host' => \$_LM_SERVER_HOST, 'host' => \$_LM_SERVER_HOST, 
'lm-server-port' => \$_LM_SERVER_PORT, 'port' => \$_LM_SERVER_PORT, 
'lm-order' => \$_LM_ORDER, 'maxDepth' => \$_LM_ORDER, 
'vocab' => \$_VOCAB, 'dictionaryPath' => \$_VOCAB, 
'lm-weight' => \$_LM_WEIGHT, 
'null-penalty' => \$_NULL_PEN, 
'word-penalty' => \$_WORD_PEN, 
'multithread' => \$_NB_THREADS,
'priors' => \@_PRIORS, 
'max-nb-tokens' => \$_MAX_NB_TOKENS, 
'nbest-size' => \$_NBEST_SIZE, 
'nbest-format' => \$_NBEST_FORMAT, 
'nbest-file' => \$_NBEST_FILE, 
'logBase' => \$_LOG_BASE, 'log-base' => \$_LOG_BASE, 
'debug-decode' => \$_DEBUG_DECODE,
'--help' => \$_HELP
); 

########################
######################## FUNCTIONS DEFINITION
my $usage = "MANY.pl \
--many-dir <MANY install directory>  : default is $_MANY_DIR
--many <MANY.jar to use>             : default is $_MANY
--config <config file>               : use parameter values in config. Each parameter value can be overriden by using the corresponding switch.
--config-type <config type>          : one of MANY, BLEU, DECODE \
--working-dir <working directory>    : default is $_WORKING_DIR \
--output <output file>               : default is $_OUTPUT \
--hyp <hypothesis file>              : repeat this param for each input hypotheses \
--reference <reference file> \
**** TERP PARAMETERS : \
--deletion <deletion cost>           : default is $_DEL_COST \
--stem <stem cost>                   : default is $_STEM_COST \
--synonym <synonym cost>             : default is $_SYN_COST \
--insertion <insertion cost>         : default is $_INS_COST \
--substitution <substitution cost>   : default is $_SUB_COST \
--match <match cost>                 : default is $_MATCH_COST \
--shift <shift cost>                 : default is $_SHIFT_COST \
--wordnet <wordnet database> \
--shift-stop-word-list <list> \
--paraphrases <paraphrase db> \
--shift-constraint <constraint>      : default is $_SHIFT_CONSTRAINT, possible values are exact or relax\
**** LM PARAMETERS \
--lm <LM filename> \
--lm-server-host <LM server hostname>: default is $_LM_SERVER_HOST \
--lm-server-port <LM server port>    : default is $_LM_SERVER_PORT \ 
--lm-order <LM order>                : default is $_LM_ORDER \
--vocab <vocab file>    \
**** DECODER PARAMETERS \
--lm-weight <lm weight>              : default is $_LM_WEIGHT \
--null-penalty <null penalty>        : default is $_NULL_PEN \
--word-penalty <word penalty>        : default is $_WORD_PEN \
--multithread <number of threads> \
--priors <systems priors> \
--max-nb-tokens <max number of tokens for decoding> : default is $_MAX_NB_TOKENS \
--nbest-size <size of the nbest list>        : default is $_NBEST_SIZE \
--nbest-format <format of the nbest list>    : default is $_NBEST_FORMAT, possible values are MOSES or BTEC \
--nbest-file <filename for the nbest list>   : default is nbest\$_NBEST_SIZE.txt \
--log-base <base for log>            : default is $_LOG_BASE \
--debug-decode                       : default is $_DEBUG_DECODE \

--help                               : print this help and exit \n";

########################
######################## Parsing parameters with GetOptions
$_HELP = 1 unless GetOptions(\%_CONFIG, 'many-dir=s', 'many=s', 'config=s', 'config-type=s', 'working-dir=s', 'output=s', 'hyp=s@', 'reference=s',
'deletion=f', 'stem=f', 'synonym=f', 'insertion=f', 'substitution=f', 'match=f', 'shift=f',
'wordnet=s', 'shift-stop-word-list=s', 'paraphrases=s', 'shift-constraint=s',
'lm=s', 'lm-server-host=s', 'lm-server-port=i', 'lm-order=i', 'vocab=s', 
'lm-weight=f', 'null-penalty=f', 'word-penalty=f', 'multithread=i', 'priors=f{,}', 'max-nb-tokens=i', 'nbest-size=i', 'nbest-format=s', 'nbest-file=s', 
'log-base=f', 'debug-decode', 'help');

######################## PREPARE DATA

die "No config type specified" unless (defined $_CONFIG_TYPE);

if(defined $_CONFIG_FILE)
{
	print STDOUT "create_many_config.pl: loading config file : $_CONFIG_FILE\n";
	scan_many_config($_CONFIG_FILE);
	dump_config(%_CONFIG);
}

die "$_MANY_DIR not found!\n" unless(-e $_MANY_DIR);
die "$_MANY not found!\n" unless(-e $_MANY);

if($_CONFIG_TYPE eq "MANY" || $_CONFIG_TYPE eq "DECODE")
{
    die "No LM specified" unless defined $_LM;
    $_LM = ensure_full_path($_LM);
    die "$_LM not found" unless (-e $_LM);

    die "No vocabulary specified" unless defined $_VOCAB;
    $_VOCAB = ensure_full_path($_VOCAB);
    die "Vocab file $_VOCAB not found" unless (-e $_VOCAB);

    $_NBEST_FILE="nbest_$_NBEST_SIZE\_.txt" if(!defined($_NBEST_FILE) && defined($_NBEST_SIZE));
}


if(@_PRIORS == 0)
{
	print STDOUT ("*********\n**** Warning: no priors given, using 0.1 for each system ****\n*********\n");
	foreach my $h (@_HYPOTHESES)
	{
		push(@_PRIORS, 0.1);
	}
}

if($_CONFIG_TYPE eq "MANY" || $_CONFIG_TYPE eq "BLEU")
{
    my @_COSTS=("--deletion", $_DEL_COST, "--stem", $_STEM_COST, "--synonym", $_SYN_COST, 
            "--insertion", $_INS_COST, "--substitution", $_SUB_COST, "--match", $_MATCH_COST, "--shift", $_SHIFT_COST);
    
    if($_SHIFT_CONSTRAINT eq "relax")
    {
        die "Wordnet directory not found: $_WORDNET" unless (-e $_WORDNET);
        die "Paraphrase DB not found: $_PARAPHRASE_DB" unless (-e $_PARAPHRASE_DB);
        die "Shift-stop-word-list not found: $_SHIFT_STOP_WORD_LIST" unless (-e $_SHIFT_STOP_WORD_LIST);
    }
}

die $usage if ($_HELP);


my $_USE_WORDNET = "true" if defined($_WORDNET);
my $_USE_PARAPHRASE_DB = "true" if defined($_PARAPHRASE_DB);

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
    print MANYCFG '<property name="port" value="'.$_LM_SERVER_PORT.'"/>'."\n";
    print MANYCFG '<property name="host" value="'.$_LM_SERVER_HOST.'"/>'."\n";
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
print MANYCFG '<property name="priors" value="'."@_PRIORS".'"/>'."\n";

print MANYCFG '<property name="multithread"     value="'.$_NB_THREADS.'"/>'."\n" if defined($_NB_THREADS);
print MANYCFG '</component>'."\n";

print MANYCFG '</config>'."\n";

#############################
# FUNCTIONS
#############################

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
     if(($name ne "priors") && ($name ne "hypotheses-cn") && ($name ne "hyp"))
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

print STDOUT " LM_SERVER_HOST: $_LM_SERVER_HOST\n";
print STDOUT " LM_SERVER_PORT: $_LM_SERVER_PORT\n";
print STDOUT " USE_LOCAL_LM: $_USE_LOCAL_LM\n";
print STDOUT " LM: $_LM\n";
print STDOUT " LM_ORDER: $_LM_ORDER\n";
print STDOUT " VOCAB: $_VOCAB\n";

print STDOUT " PRIORS: @_PRIORS\n";
print STDOUT " NULL_PEN: $_NULL_PEN\n";
print STDOUT " WORD_PEN: $_WORD_PEN\n";
print STDOUT " LM_WEIGHTS: $_LM_WEIGHT\n";
print STDOUT " MAX_NB_TOKENS: $_MAX_NB_TOKENS\n";
print STDOUT " NBEST_SIZE: $_NBEST_SIZE\n";
print STDOUT " NBEST_FORMAT: $_NBEST_FORMAT\n";
print STDOUT " NBEST_FILE: $_NBEST_FILE\n";
print STDOUT " DEBUG_DECODE: $_DEBUG_DECODE\n";
print STDOUT " LOG_BASE: $_LOG_BASE\n";
print STDOUT "------ END VARS -------\n";

}

