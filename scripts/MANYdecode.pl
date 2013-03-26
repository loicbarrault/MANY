#!/usr/bin/perl
##!/opt/local/bin/perl

use strict;
#use warnings;
use File::Path;
use Cwd;
use Getopt::Long;

########### VARIABLES DEFINITION

# Tools
my $_MANY_DIR="$ENV{HOME}/src/MANY";
my $_MANY="$_MANY_DIR/lib/MANY.jar";
my $_CHECK_LM_SERVER="$_MANY_DIR/resources/SockTester.jar";

# General parameters
my $_MANY_CONFIG="many.config.xml";
my $_OUTPUT=undef;
my $_CURRENT_DIR=cwd();
my $_WORKING_DIR;
my $_CONFIG_FILE=undef;
my @_HYPOTHESES=();
my $_NB_SYS=undef;
my $_NB_THREADS=undef;
my $_PRIORS_AS_CONFIDENCE="true";

# LM parameters
#my $_LM_SERVER_HOST=`hostname`; chomp $_LM_SERVER_HOST;
my $_LM_SERVER_HOST=`hostname`; chomp $_LM_SERVER_HOST;
my $_LM_SERVER_PORT=-1;
my $_USE_LOCAL_LM=0;
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
'many-dir' => \$_MANY_DIR, 
'many' => \$_MANY, 
'config' => \$_CONFIG_FILE, 
'working-dir' => \$_WORKING_DIR,
'output' => \$_OUTPUT, 
'hyp' => \@_HYPOTHESES, 'hypotheses-cn' => \@_HYPOTHESES, 
'lm' => \$_LM,
'lm-server-host' => \$_LM_SERVER_HOST, 'host' => \$_LM_SERVER_HOST, 
'lm-server-port' => \$_LM_SERVER_PORT, 'port' => \$_LM_SERVER_PORT, 
'lm-order' => \$_LM_ORDER, 'maxDepth' => \$_LM_ORDER, 
'vocab' => \$_VOCAB, 'dictionaryPath' => \$_VOCAB,
'use-local-lm' => \$_USE_LOCAL_LM,
'lm-weight' => \$_LM_WEIGHT, 
'null-penalty' => \$_NULL_PEN, 
'word-penalty' => \$_WORD_PEN, 
'multithread' => \$_NB_THREADS,
'priors' => \@_PRIORS, 
'priors-as-confidence' => \$_PRIORS_AS_CONFIDENCE, 
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
my $usage = "MANYdecode.pl \
--many-dir <MANY install directory>  : default is $_MANY_DIR
--many <MANY.jar to use>             : default is $_MANY
--config <config file>               : use parameter values in config. Each parameter value can be overriden by using the corresponding switch.
--working-dir <working directory>    : default is $_WORKING_DIR \
--output <output file>               : default is $_OUTPUT \
--hyp <hypothesis file>              : repeat this param for each input hypotheses \
**** LM PARAMETERS \
--lm <LM filename> \
--lm-server-host <LM server hostname>: default is $_LM_SERVER_HOST \
--lm-server-port <LM server port> \
--lm-order <LM order>   \
--vocab <vocab file>    \
--use-local-lm \
**** DECODER PARAMETERS \
--lm-weight <lm weight>              : default is $_LM_WEIGHT \
--null-penalty <null penalty>        : default is $_NULL_PEN \
--word-penalty <word penalty>        : default is $_WORD_PEN \
--multithread <number of threads> \
--priors <systems priors> \
--priors-as-confidence               : use priors as word confidence score \
--max-nb-tokens <max number of tokens for decoding> : default is $_MAX_NB_TOKENS \
--nbest-size <size of the nbest list>        : default is $_NBEST_SIZE \
--nbest-format <format of the nbest list>    : default is $_NBEST_FORMAT, possible values are MOSES or BTEC \
--nbest-file <filename for the nbest list>   : default is nbest\$_NBEST_SIZE.txt \
--log-base <base for log>            : default is $_LOG_BASE \
--debug-decode                       : default is $_DEBUG_DECODE \

--help                               : print this help and exit \n";

########################
######################## Parsing parameters with GetOptions
$_HELP = 1 unless GetOptions(\%_CONFIG, 'many-dir=s', 'many=s', 'config=s', 'working-dir=s', 'output=s', 'hyp=s@',
'lm=s', 'lm-server-host=s', 'lm-server-port=i', 'lm-order=i', 'vocab=s', 'use-local-lm', 
'lm-weight=f', 'null-penalty=f', 'word-penalty=f', 'multithread=i', 'priors=f{,}', 'priors-as-confidence=s', 'max-nb-tokens=i', 'nbest-size=i', 'nbest-format=s', 'nbest-file=s', 
'log-base=f', 'debug-decode', 'help');

######################## PREPARE DATA

if(defined $_CONFIG_FILE)
{
	print STDOUT "MANYdecode: loading config file : $_CONFIG_FILE\n";
	scan_many_config($_CONFIG_FILE);
	#dump_config(%_CONFIG);
}

die "$_MANY_DIR not found!\n" unless(-e $_MANY_DIR);
die "$_MANY not found!\n" unless(-e $_MANY);

die "No LM specified" unless defined $_LM;
$_LM = ensure_full_path($_LM);
die "$_LM not found" unless (-e $_LM);


die "No vocabulary specified" unless defined $_VOCAB;
$_VOCAB = ensure_full_path($_VOCAB);
die "$_VOCAB not found" unless (-e $_VOCAB);

$_NBEST_FILE="nbest_$_NBEST_SIZE\_.txt" if(!defined($_NBEST_FILE) && defined($_NBEST_SIZE));

if(@_PRIORS == 0)
{
	print STDOUT ("*********\n**** Warning: no priors given, using 0.1 for each system ****\n*********\n");
	foreach my $h (@_HYPOTHESES)
	{
		push(@_PRIORS, 0.1);
	}
}

die $usage if ($_HELP);

$_NB_SYS = @_HYPOTHESES;
die "Please, specify at least two CN for system combination ...\n" if($_NB_SYS < 2);
die "Please, specify a working directory ...\n" if !defined($_WORKING_DIR);

#dump_vars();

print STDOUT "MANYdecode - start time is : ".`date`."\n";

print STDOUT "Using local LM $_LM\n" if($_USE_LOCAL_LM);
print STDOUT "Using LMSERVER on host $_LM_SERVER_HOST, port $_LM_SERVER_PORT\n" if(!$_USE_LOCAL_LM);


print STDOUT "Creating working directory : $_WORKING_DIR ...";
mkpath $_WORKING_DIR;
print STDOUT " OK \n";

print STDOUT "Entering $_WORKING_DIR ... \n";
chdir $_WORKING_DIR;

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
        $nb_sent = 0;
        foreach my $l (@lines)
        {
            $nb_sent++ if($l =~ /^name/);
        }
        die "No sentence to process ... " if($nb_sent==0);
        print STDOUT "$nb_sent sentences to process ...\n";
    }
    else
    {
        # Sanity check of hypotheses
	    open(FH, "<$input_abs");
	    my @lines = <FH>;
	    close(FH);
        my $nsent = 0;
        foreach my $l (@lines)
        {
            $nsent++ if($l =~ /^name/);
        }
	    die "\nERROR : Hypotheses files $_HYPOTHESES[0] and $input have not the same size" if($nsent != $nb_sent);
    }
}
@_HYPOTHESES = @_HYPS_ABS;
#print "HYPS AFTER : @_HYPOTHESES\n";
print STDOUT "hypotheses files: OK \n";

die "No VOCAB file specified ... exiting!\n" unless defined $_VOCAB;
print STDOUT "Using vocabulary file $_VOCAB\n";

# Cleaning working dir
if($_CLEAN > 0)
{
	print STDOUT "Cleaning working directory $_WORKING_DIR ...";
	unlink $_OUTPUT;
	print STDOUT " OK \n";
}


######################## LAUNCH LMSERVER if needed
my $check="DOWN";
if(!$_USE_LOCAL_LM) #if not local lm
{
	$_CHECK_LM_SERVER="$_MANY_DIR/resources/SockTester.jar";
	die "$_CHECK_LM_SERVER not found!\n" unless (-e $_CHECK_LM_SERVER);
    $check=`java -jar $_CHECK_LM_SERVER -h $_LM_SERVER_HOST -p $_LM_SERVER_PORT`;
    if(!($check =~ /UP/))
    {
        print STDOUT "Launching $_LM on host $_LM_SERVER_HOST, port $_LM_SERVER_PORT\n"; 
        #print STDOUT "nohup ngram -order $_LM_ORDER -lm $_LM -server-port $_LM_SERVER_PORT -unk </dev/null >nohup.out 2>&1 &";
        `nohup ngram -order $_LM_ORDER -lm $_LM -server-port $_LM_SERVER_PORT -unk </dev/null >nohup.out 2>&1 &`;
    }
    else
    {
        print STDOUT "LM server already running on $_LM_SERVER_HOST on port $_LM_SERVER_PORT .. using it (supposing it's the correct LM)\n";
    }
}

######################## GENERATE MANY CONFIG FILE
## Build command

# The arguments for generate_config function
my @cfg =();
push(@cfg, ("--config-type", "DECODE", "--nbsys", "$_NB_SYS", "--output", "$_OUTPUT"));

foreach my $f (@_HYPOTHESES)
{
    push(@cfg, ("--hyp", $f));
}

push(@cfg, ("--vocab", $_VOCAB));

if($_USE_LOCAL_LM) #local lm
{
    #print "***************create_MANY_config: LOCAL LM******************\n";
    push(@cfg, ("--lm", $_LM, "--lm-order", $_LM_ORDER));
}
else #lm serveur
{
    push(@cfg, ("--lm-server-host", $_LM_SERVER_HOST));
    push(@cfg, ("--lm-server-port", $_LM_SERVER_PORT));
    push(@cfg, ("--lm-order", $_LM_ORDER));
}

push(@cfg, ("--lm-weight", $_LM_WEIGHT, "--null-penalty", $_NULL_PEN, "--word-penalty", $_WORD_PEN));
push(@cfg, ("--priors", @_PRIORS));
push(@cfg, ("--priors-as-confidence", $_PRIORS_AS_CONFIDENCE));

push(@cfg, ("--max-nb-tokens", $_MAX_NB_TOKENS)) if(defined($_MAX_NB_TOKENS));
push(@cfg, ("--nbest-size", $_NBEST_SIZE)) if(defined($_NBEST_SIZE));
push(@cfg, ("--nbest-format", $_NBEST_FORMAT)) if(defined($_NBEST_FORMAT));
push(@cfg, ("--nbest-file", $_NBEST_FILE)) if(defined($_NBEST_FILE));
push(@cfg, ("--log-base", $_LOG_BASE)) if(defined($_LOG_BASE));
push(@cfg, ("--multithread", $_NB_THREADS)) if(defined($_NB_THREADS));
push(@cfg, "--debug-decode") if(defined($_DEBUG_DECODE) and $_DEBUG_DECODE!="false");

print STDERR "------------------------------\nMANYdecode CONFIG : ";
print STDERR join(" ", @cfg);
print STDERR "\n------------------------------\n";

create_MANY_config(@cfg);
print STDOUT " done !\n";

######################## WAIT FOR THE LM SERVER TO BE READY 
# - This should be included in the MANY software at some point

if(!$_USE_LOCAL_LM)
{
    print STDOUT "\n";
    my $res="DOWN";
    print STDOUT "Waiting for the LM server to be ready ";
    while(! ($res =~ /UP/))
    {
        #for(my $i=0; $i<10; $i++)
        #{
            #print STDOUT "java -jar $_CHECK_LM_SERVER -h $_LM_SERVER_HOST -p $_LM_SERVER_PORT\n";
            $res=`java -jar $_CHECK_LM_SERVER -h $_LM_SERVER_HOST -p $_LM_SERVER_PORT`; #try to connect
            sleep(1);
            print STDOUT ".";
        #}
        #if(!($res =~ /UP/))
        #{
        #    print STDOUT "\b" x 10, " " x 10, "\r";
    #}
    }
    print STDOUT "\n";
}
######################## CALL MANY

my $enc="UTF-8";
#my $enc="ISO_8859_1";

print STDOUT "CMD : java -Xmx4G -Dfile.encoding=".$enc." -cp $_MANY edu.lium.mt.MANYdecode $_MANY_CONFIG\n";
print STDOUT "Starting MANY system combination ...";
print STDOUT "\n------- START LOG ----\n";
safesystem("java -Xmx4G -Dfile.encoding=$enc -cp $_MANY edu.lium.mt.MANYdecode $_MANY_CONFIG");
print STDOUT "\n------- END LOG ----\n";
print STDOUT " OK \n";

chdir "./..";

#print STDOUT "MANY.sh - end time is : ".DateTime->now()->datetime()."\n";

######################## KILL THE LM SERVER if it has been launched


if((!$_USE_LOCAL_LM) && ($check =~ /UP/))
{
    #kill_lm_server($_LM_SERVER_HOST);
}

######################## FUNCTIONS
sub kill_lm_server()
{
    my $host = $_[0];
    #print "Hostname : $host\n";
    
    #print STDOUT "ssh $host ps -efl | grep $ENV{USER} | grep ngram | head -n 1 | tr -s \"\t\" \" \" | cut -f4 -d\" \"\n";
    #my $res = `ssh $host ps -efl | grep $ENV{USER} | grep ngram | head -n 1 | tr -s "\t" " " | cut -f4 -d" "`;
    
    #print STDOUT "ps -efl -u $ENV{USER} | grep ngram | head -n 1 | tr -s \"\t\" \" \" | cut -f3 -d\" \"\n";
    my $res = `ps -efl -u $ENV{USER} | grep ngram | head -n 1 | tr -s "\t" " " | cut -f3 -d" "`;
    
    #safesystem("ssh $host ps -efl | grep $ENV{USER} | grep ngram | head -n 1 | tr -s \"\t\" \" \" | cut -f4 -d\" \"");
    #print STDOUT "Les PIDs : >$res<\n";
    #`ssh $host kill -9 $res`;
    
    safesystem("ssh $host kill -9 $res");
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
$_PRIORS_AS_CONFIDENCE,
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
"true",
5000, 0, "MOSES", "nbest.txt",
"true", undef, undef, "true", "true",
undef,
undef,
undef,
"relax", "terp.params",
"localhost", -1, undef, 4, undef,
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
--lm-server-port <LM srever port> \
--lm <ngram LM in DMP32 format>  \
--lm-order <LM order>                  : default is $_LM_ORDER \
--vocab <vocab file> \
**** DECODER PARAMETERS : \
--lm-weight <lm weight>                : default is $_LM_WEIGHT \
--null-penalty <null penalty>          : default is $_NULL_PEN \
--word-penalty <word penalty>          : default is $_WORD_PEN \
--multithread <number of thread> \
--priors <systems priors>              : default is @_SYS_PRIORS \
--priors-as-confidence                 : default is $_PRIORS_AS_CONFIDENCE \
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
                       'priors-as-confidence=s' => \$_PRIORS_AS_CONFIDENCE,
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
print MANYCFG '<property name="use-local-lm" value="';
($_LMSERVER_PORT==-1)?print MANYCFG "true":print MANYCFG "false";
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
    
    if($_LMSERVER_PORT!=-1)
    {
        print MANYCFG '<component  name="lmonserver" type="edu.cmu.sphinx.linguist.language.ngram.NetworkLanguageModel">'."\n";
        print MANYCFG '<property name="port" value="'.$_LMSERVER_PORT.'"/>'."\n";
        print MANYCFG '<property name="host" value="'.$_LMSERVER_HOST.'"/>'."\n";
        print MANYCFG '<property name="maxDepth" value="'.$_LM_ORDER.'"/>'."\n";
        print MANYCFG '<property name="logMath" value="logMath"/>'."\n";
        print MANYCFG '</component>'."\n";

        print MANYCFG "\n";
    }
    else # local LM
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
print MANYCFG '<property name="priors-as-confidence" value="'."$_PRIORS_AS_CONFIDENCE".'"/>'."\n";

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
print STDOUT " CHECK_LM_SERVER: $_CHECK_LM_SERVER\n";

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

