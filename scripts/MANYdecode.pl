#!/usr/bin/perl
##!/opt/local/bin/perl

use strict;
#use warnings;
use File::Path;
use Cwd;
use Getopt::Long;

########### VARIABLES DEFINITION

# Tools
my $_MANY_DIR="$ENV{MANY_HOME}";
my $_MANY="$_MANY_DIR/lib/MANY.jar";
my $_CHECK_LM_SERVER="$_MANY_DIR/resources/SockTester.jar";

my $create_MANY_config="$_MANY_DIR/scripts/create_many_config.pl";

# General parameters
my $_MANY_CONFIG="manydecode.config.xml";
my $_OUTPUT=undef;
my $_ALTERNATIVES=undef;
my $_CURRENT_DIR=cwd();
my $_WORKING_DIR;
my $_CONFIG_FILE=undef;
my @_SRC=();
my $_SRC_FORMAT="TXT"; # possible choices : TXT or JSON
my @_HYPOTHESES=();
my @_HYPOTHESES_CN=();
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
my $_NBEST_FORMAT="MOSES";
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
'alternatives' => \$_ALTERNATIVES, 
'hyp' => \@_HYPOTHESES, 'hyp-cn' => \@_HYPOTHESES_CN, 
'src' => \@_SRC,
'src-format' => \$_SRC_FORMAT,
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
'help' => \$_HELP
); 

########################
######################## FUNCTIONS DEFINITION
my $usage = "MANYdecode.pl \
--many-dir <MANY install directory>  : default is $_MANY_DIR
--many <MANY.jar to use>             : default is $_MANY
--config <config file>               : use parameter values in config. Each parameter value can be overriden by using the corresponding switch.
--working-dir <working directory>    : \
--output <output file>               : \
--hyp <hypothesis file>              : repeat this param for each input hypothesis \
--hyp-cn <CN hypothesis file>        : repeat this param for each input confusion network \
--src <source file>                  : repeat this param for each source file \
--src-format <src file format>       : allowed values are TXT or JSON, default is $_SRC_FORMAT \
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
--nbest-file <filename for the nbest list>   : default is nbest<nbest-size value>.txt \
--log-base <base for log>            : default is $_LOG_BASE \
--debug-decode                       : default is $_DEBUG_DECODE \

--help                               : print this help and exit \n";

########################
######################## Parsing parameters with GetOptions
$_HELP = 1 unless GetOptions(\%_CONFIG, 'many-dir=s', 'many=s', 'config=s', 'working-dir=s', 'output=s', 'alternatives=s', 'hyp=s@', 'hyp-format=s', 'hyp-cn=s@', 'src=s@', 'src-format=s',
'lm=s', 'lm-server-host=s', 'lm-server-port=i', 'lm-order=i', 'vocab=s', 'use-local-lm', 
'lm-weight=f', 'null-penalty=f', 'word-penalty=f', 'multithread=i', 'priors=f{,}', 'priors-as-confidence=s', 'max-nb-tokens=i', 'nbest-size=i', 'nbest-format=s', 'nbest-file=s', 
'log-base=f', 'debug-decode', 'help');

######################## PREPARE DATA

die $usage if ($_HELP);

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


print STDOUT "Creating working directory : $_WORKING_DIR/ ...";
mkpath $_WORKING_DIR;
print STDOUT " OK \n";

print STDOUT "Entering working directory $_WORKING_DIR/ ... \n";
chdir $_WORKING_DIR;

print STDOUT "Using $_NB_SYS systems ...\n";
print STDOUT "Checking size of hypotheses files ... ";
my $nb_sent = undef;

#print "HYPS BEFORE : @_HYPOTHESES_CN\n";
my @_HYPS_ABS = (); # required, input texts to combine (absolute)
foreach my $input (@_HYPOTHESES_CN) {
	my $input_abs = ensure_full_path($input);
    die "$input not found (interpreted as $input_abs)." unless (-e $input_abs);
    push(@_HYPS_ABS, $input_abs);
    if(!defined $nb_sent) { 
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
    } else {
        # Sanity check of hypotheses
	    open(FH, "<$input_abs");
	    my @lines = <FH>;
	    close(FH);
        my $nsent = 0;
        foreach my $l (@lines)
        {
            $nsent++ if($l =~ /^name/);
        }
	    die "\nERROR : Hypotheses files $_HYPOTHESES_CN[0] and $input have not the same size" if($nsent != $nb_sent);
    }
}
@_HYPOTHESES_CN = @_HYPS_ABS;
#print "HYPS AFTER : @_HYPOTHESES\n";
print STDOUT " --> hypotheses files: OK \n";

die "No VOCAB file specified ... exiting!\n" unless defined $_VOCAB;
print STDOUT "Using vocabulary file $_VOCAB\n";

# Cleaning working dir
if($_CLEAN > 0) {
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
	#`ngram -order $_LM_ORDER -lm $_LM -server-port $_LM_SERVER_PORT -unk </dev/null > lm.out 2>&1 &`;
    }
    else
    {
        print STDOUT "LM server already running on $_LM_SERVER_HOST on port $_LM_SERVER_PORT .. using it (supposing it's the correct LM)\n";
    }
}

######################## GENERATE MANY CONFIG FILE
## Build command

# The arguments for create_MANY_config script
my @cmd =("$create_MANY_config");
push(@cmd, ("--output-config", $_MANY_CONFIG ));
push(@cmd, ("--config-type", "DECODE", "--output", "$_OUTPUT"));
push(@cmd, ("--alternatives", $_ALTERNATIVES)) if(defined($_ALTERNATIVES));

foreach my $f (@_HYPOTHESES_CN) {
    push(@cmd, ("--hyp-cn", $f));
}
#in case we need the initial hyps at some point (these are not the confusion networks)
foreach my $f (@_HYPOTHESES) {
    push(@cmd, ("--hyp", $f));
}
#push(@cmd, ("--hyp-format", $_HYP_FORMAT));

#in case we need the source at some point
foreach my $f (@_SRC) {
    push(@cmd, ("--src", $f));
}
push(@cmd, ("--src-format", $_SRC_FORMAT));

push(@cmd, ("--vocab", $_VOCAB));

if($_USE_LOCAL_LM) { #local lm
    #print "***************create_MANY_config: LOCAL LM******************\n";
    push(@cmd, ("--lm", $_LM)); 
}
else { #lm server
    push(@cmd, ("--lm-server-host", $_LM_SERVER_HOST));
    push(@cmd, ("--lm-server-port", $_LM_SERVER_PORT));
}

push(@cmd, ("--lm-order", $_LM_ORDER));
push(@cmd, ("--lm-weight", $_LM_WEIGHT, "--null-penalty", $_NULL_PEN, "--word-penalty", $_WORD_PEN));
push(@cmd, ("--priors", @_PRIORS));
push(@cmd, ("--priors-as-confidence", $_PRIORS_AS_CONFIDENCE));

push(@cmd, ("--max-nb-tokens", $_MAX_NB_TOKENS)) if(defined($_MAX_NB_TOKENS));
push(@cmd, ("--nbest-size", $_NBEST_SIZE)) if(defined($_NBEST_SIZE));
push(@cmd, ("--nbest-format", $_NBEST_FORMAT)) if(defined($_NBEST_FORMAT));
push(@cmd, ("--nbest-file", $_NBEST_FILE)) if(defined($_NBEST_FILE));
push(@cmd, ("--log-base", $_LOG_BASE)) if(defined($_LOG_BASE));
push(@cmd, ("--multithread", $_NB_THREADS)) if(defined($_NB_THREADS));
push(@cmd, "--debug-decode") if(defined($_DEBUG_DECODE) and $_DEBUG_DECODE!="false");

#print STDERR "------------------------------\nMANYdecode VARS: ";
#dump_vars();
print STDERR "------------------------------\nMANYdecode CONFIG: ";
print STDERR join(" ", @cmd);
print STDERR "\n------------------------------\n";

#create_MANY_config(@cmd);
safesystem(@cmd);
print STDOUT "  --> config created!\n";

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

sub scan_many_config() {
	my $xml = shift;
  	my $xmlshortname = $xml; $xmlshortname =~ s/^.*\///; 
  	open XML, $xml or die "Can't read $xml";
  	my $nr = 0;
  	my $error = 0;
	while (<XML>) {
		$nr++;
		chomp;
		next if /^\s*#/; # skip comments
		next if /^\s*$/; # skip blank lines
		
		if(/^<property name=\"(.+)\"\s+value=\"(.+)\"/) {
			my $param_name = $1;
			my $param_val = $2;
			
			$param_val =~ s/file:// if ($param_name eq "dictionaryPath"); 
			
			if(exists($_CONFIG{$param_name})) {
				if(($param_name ne "priors") && ($param_name ne "hypotheses-cn") && ($param_name ne "hypotheses")) {
					if(!defined(${$_CONFIG{$param_name}})) {
				   		${$_CONFIG{$param_name}} = $param_val;
				   	}
				   	#else{ print "$param_name already defined to val >${$_CONFIG{$param_name}}<\n"; }
				} else {
				    if(!defined(@{$_CONFIG{$param_name}})) {
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
     if(($name ne "priors") && ($name ne "hypotheses-cn")) {	
     	print STDOUT "PARAM: $name\tVAL: ${$config{$name}}\n";
     } else {
     	print STDOUT "PARAM: $name\n";
     	my $i=0; 
     	foreach my $v (@{$config{$name}}) {
     		print STDOUT "\tVAL[$i]: $v\n";
     		$i++
     	}
     }
  }
  print STDOUT "------ CONFIG -------\n";
}

sub dump_vars() {
print STDERR "------ VARS -------\n";
print STDERR " MANY_DIR: $_MANY_DIR\n";
print STDERR " MANY: $_MANY\n";
print STDERR " CHECK_LM_SERVER: $_CHECK_LM_SERVER\n";

print STDERR " MANY_CONFIG: $_MANY_CONFIG\n";
print STDERR " OUTPUT: $_OUTPUT\n";
print STDERR " CURRENT_DIR: $_CURRENT_DIR\n";
print STDERR " WORKING_DIR: $_WORKING_DIR\n";
print STDERR " CONFIG_FILE: $_CONFIG_FILE\n";
print STDERR " HYPOTHESES: @_HYPOTHESES\n";
print STDERR " NB_SYS: $_NB_SYS\n";
print STDERR " NB_THREADS: $_NB_THREADS\n";

print STDERR " LM_SERVER_HOST: $_LM_SERVER_HOST\n";
print STDERR " LM_SERVER_PORT: $_LM_SERVER_PORT\n";
print STDERR " USE_LOCAL_LM: $_USE_LOCAL_LM\n";
print STDERR " LM: $_LM\n";
print STDERR " LM_ORDER: $_LM_ORDER\n";
print STDERR " VOCAB: $_VOCAB\n";

print STDERR " PRIORS: @_PRIORS\n";
print STDERR " NULL_PEN: $_NULL_PEN\n";
print STDERR " WORD_PEN: $_WORD_PEN\n";
print STDERR " LM_WEIGHTS: $_LM_WEIGHT\n";
print STDERR " MAX_NB_TOKENS: $_MAX_NB_TOKENS\n";
print STDERR " NBEST_SIZE: $_NBEST_SIZE\n";
print STDERR " NBEST_FORMAT: $_NBEST_FORMAT\n";
print STDERR " NBEST_FILE: $_NBEST_FILE\n";
print STDERR " DEBUG_DECODE: $_DEBUG_DECODE\n";
print STDERR " LOG_BASE: $_LOG_BASE\n";
print STDERR "------ END VARS -------\n";

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

sub safebackticks {
  print STDERR "Executing: @_\n";
  my $ret = `@_`;
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
    #return ! $exitcode;
  }
  chomp $ret;
  return $ret;
}

