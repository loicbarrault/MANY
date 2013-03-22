#!/usr/bin/perl

use strict;
use Getopt::Long;
use Cwd;

# Steps overview
# -- Optimization on DEV
# (1) Call CONDOR to optimize TERp parameters
# (1.1) prepare data
# (1.2) Generate script containing MANYbleu call
# (1.3) Generate script containing xmlOptimizer call
# (1.4) Run optimization in qsub
# -- Evaluation on TEST
# (2) Call mert-manydecode to optimize decoder parameters
# (2.1) prepare data
# (2.2) Generate script containing mert-manydecode call
# (2.3) Run optimization in qsub

###################
# DATA
###################
# -- Global use variables
my $home="$ENV{HOME}";
my $release_dir="$home/RELEASE/bin";
my $tool_dir="$home/TOOLS";
my $many_dir="$home/SRC/MANY";
my $many_scripts_dir="$many_dir/scripts";
my $current_dir=cwd();

my $many="$release_dir/MANY_501.jar";


my $name="_newsdev2009a_"; #unique name of the experiment
my $data_dir="/lium/trad2/barrault/SYSCOMB/wmt/wmt09/fr-en/news-dev2009a/bleu_recall/perl_5sys";
my $reference="$data_dir/news-dev2009a.en.tok";
my @hyps=qw( fr-en.news-dev2009a.lium-systran.txt.tok fr-en.news-dev2009a.limsi.txt.tok fr-en.news-dev2009a.uedin.txt.tok fr-en.news-dev2009a.cued.txt.tok fr-en.news-dev2009a.rbmt5.txt.tok );
foreach my $h (@hyps)
{
    $h = "$data_dir/$h";
}
my $nbsys = scalar @hyps;

#Other variables
my $log_base=2.718;


######################
# MAIN PROGRAM
######################

# -- Optimization of TERp parameters on DEV
# (1) Call CONDOR to optimize TERp parameters
print STDOUT "(1) Call CONDOR to optimize TERp parameters\n";
# (1.1) prepare data
print STDOUT "(1.1) prepare data\n";
# -- For TERp parameter optimization only
my $manybleu_script="manybleu.pl";
my $optim_script="optim.pl";
my $optimize_manybleu="$many_scripts_dir/Optimize_MANYbleu.pl";
my $optim_working_dir="manybleu";
my $optim_output="manybleu.output";
my @priors=();
my $wordnet="/lium/buster1/barrault/TOOLS/WordNet-3.0/dict";
my $paraphrases="/lium/buster1/barrault/TOOLS/terp/terp.v1/data/phrases.db";
my $stop_list="/lium/buster1/barrault/TOOLS/terp/terp.v1/data/shift_word_stop_list.txt";
my $shift_constraint="relax";
my $nb_threads=$nbsys>8?8:$nbsys; 
# (1.2) Generate script containing MANYbleu call
print STDOUT "(1.2) Generate script containing MANYbleu call\n";
my @cmd = ("time", $optimize_manybleu, "--many", $many);
push(@cmd, ("--working-dir", $optim_working_dir));
push(@cmd, ("--output", $optim_output));
foreach my $h (@hyps)
{
    push(@cmd, ("--hyp", $h));
}
push(@cmd, ("--reference", $reference));
push(@cmd, ("--wordnet", $wordnet, "--paraphrases", $paraphrases));
push(@cmd, ("--shift-stop-word-list", $stop_list, "--shift-constraint", $shift_constraint));
push(@cmd, ("--multithread", $nb_threads)) if(defined $nb_threads);
push(@cmd, ("--priors", @priors)) if(scalar @priors > 0);
push(@cmd, ("--log-base", $log_base)) if(defined $log_base);


&generate_perl_script($manybleu_script, 1, $nb_threads, 30, "condor_manybleu.log", @cmd);
chmod 0755, $manybleu_script;

# (1.3) Generate condor.xml 
print STDOUT "(1.3) Generate condor.xml\n"; 
my $condor_xml="condor_manybleu.xml";
my $condor_basename="condor_manybleu";
&generate_condor_xml("$condor_basename.xml", $manybleu_script, $condor_basename);

# (1.4) Generate script containing xmlOptimizer call
print STDOUT "(1.4) Generate script containing xmlOptimizer call\n";
my @optim_cmd=();
push(@optim_cmd, ("time", "xmlOptimizer", $condor_xml));
print STDOUT "CMD : ".join(" ", @optim_cmd)."\n";
&generate_perl_script($optim_script, 1, $nb_threads, 30, "$condor_basename.log", @optim_cmd);
chmod 0755, $optim_script;

# (1.5) Run optimization in qsub
print STDOUT "(1.5) Run optimization in qsub\n";
my @qsub_cmd = ("qsub", $optim_script);
my $optim_pid=safebackticks(@qsub_cmd);
print STDOUT "OPTIM PID = $optim_pid\n";

####################################################################
####################################################################
# -- Optimization of DECODER parameters on DEV
####################################################################
####################################################################
# (2) Call mert-manydecode to optimize decoder parameters
# (2.1) prepare data
# (2.2) Generate many.config.xml
# (2.3) Generate script containing mert-manydecode call
# (2.4) Run optimization in qsub


# (2.1) prepare data
# -- For DECODER parameter optimization only
my $decod_script="mert_$name.pl";
my $moses_rootdir="/opt/mt/mosesdecoder/moses-cmd/";
my $mert_manydecode="$many_scripts_dir/mert-manydecode.pl";
my $many_decode="$many_scripts_dir/MANYdecode.pl";
my $many_config="many.config.xml";
my $decod_working_dir="mert-manydecode";
my $decod_output="many.output";
my $host=`hostname`; chomp $host;
my $port=1234;
my $order=4;
my $datadir="/lium/trad2/barrault/SYSCOMB/wmt/wmt09/fr-en/news-dev2009a/data";
my $lm="$datadir/LMs/newsep5nc10ungw4.newst08.wmt10.4g.kn-int.sblm";
my $vocab="$datadir/LMs/wmt10.vocab";
my @decod_cns=();
my $nbest_size=300;
my $nbest_file="best$nbest_size.txt";
my $nbest_format="MOSES";
my $max_nb_tokens=10000;
my $debug_decode=undef;
my $seed="";
#my $seed=689438581;

my @costs=("--deletion", 1.0, "--stem", 1.0, "--synonym", 1.0, "--insertion", 1.0, "--substitution", 1.0, "--match", 0.0, "--shift", 1.0);
my @priors=();
foreach my $h (@hyps)
{
    push(@priors, 0.1);
}

# (2.2) Generate many.config.xml
my @cfg =();
push(@cfg, ("--config-type", "DECODE", "--nbsys", $nbsys, "--output", "$decod_output"));

for(my $i=0; $i<=$#hyps; $i++)
{
    my $cn = "$current_dir/$optim_working_dir/BEST.$optim_output.cn.$i";
    push(@decod_cns, $cn);
    push(@cfg, ("--hyp", $cn));
    #push(@cfg, ("--hyp", $f));
}
push(@cfg, ("--vocab", $vocab));
push(@cfg, ("--lm-server-host", $host, "--lm-server-port", $port, "--lm-order", $order));

#push(@cfg, ("--lm-weight", $_LM_WEIGHT, "--null-penalty", $_NULL_PEN, "--word-penalty", $_WORD_PEN)); 
push(@cfg, ("--priors", @priors));
push(@cfg, @costs);
push(@cfg, ("--shift-constraint", $shift_constraint));

if($shift_constraint eq "relax")
{
    push(@cfg, ("--paraphrase-db", $paraphrases)) if(defined $paraphrases);
    push(@cfg, ("--shift-stop-word-list", $stop_list));
    push(@cfg, ("--wordnet", $wordnet)) if defined($wordnet);
}

push(@cfg, ("--max-nb-tokens", $max_nb_tokens)) if(defined($max_nb_tokens));
push(@cfg, ("--nbest-size", $nbest_size)) if(defined($nbest_size));
push(@cfg, ("--nbest-format", $nbest_format)) if(defined($nbest_format));
push(@cfg, ("--nbest-file", $nbest_file)) if(defined($nbest_file));
push(@cfg, ("--log-base", $log_base)) if(defined($log_base));
push(@cfg, ("--multithread", $nb_threads)) if(defined($nb_threads));
push(@cfg, "--debug-decode") if(defined($debug_decode) and $debug_decode!="false");

print STDERR "------------------------------\nCONFIG : ";
print STDERR join(" ", @cfg);
print STDERR "\n------------------------------\n";

&create_MANY_config($many_config, @cfg);

# (2.3) Generate script containing mert-manydecode call
my @decod_cmd=();
push(@decod_cmd, ("time", $mert_manydecode, $reference, $many_decode, $many_config, @decod_cns, "--working-dir", $decod_working_dir, "--rootdir", $moses_rootdir, "--starting-weights-from-xml"));
push (@decod_cmd, ("--mertargs", "-r $seed")) if $seed;
push (@decod_cmd, ("--decoder-flags", "--working-dir . --lm $lm --lm-order $order --lm-server-port $port --vocab $vocab --log-base $log_base --max-nb-tokens $max_nb_tokens"));
#--lm-server-host $host 
print STDOUT "CMD : ".join(" ", @decod_cmd)."\n";
&generate_perl_script_depend($decod_script, 1, 2, 30, "mert_manydecode.log", $optim_pid, @decod_cmd);
chmod 0755, $decod_script;

# (2.4) Run optimization in qsub
@qsub_cmd = ("qsub", $decod_script);
safesystem(@qsub_cmd);




###################
# FUNCTIONS
###################
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
sub generate_perl_script()
{
    my ($script, $nodes, $ppn, $mem, $log, @args) = @_;
    
    open(SCRIPT, ">$script") or die "Can't create file $script! $!";

print SCRIPT "#!/usr/bin/perl
#PBS -q trad -d . -V  
#PBS -l nodes=$nodes:ppn=$ppn 
#PBS -l mem=${mem}g 
#PBS -l cput=1000:00:00  
#PBS -j oe 
#PBS -o $log 
use strict;
######################
#  FUNCTIONS
######################
sub safesystem {
  print STDERR \"Executing: \@_\\n\";
  system(\@_);
  if (\$? == -1) {
      print STDERR \"Failed to execute: \@_\\n  \$!\\n\";
      exit(1);
  }
  elsif (\$? & 127) {
      printf STDERR \"Execution of: \@_\\n  died with signal %d, %s coredump\\n\",
          (\$? & 127),  (\$? & 128) ? 'with' : 'without';
      exit(1);
  }
  else {
    my \$exitcode = \$? >> 8;
    print STDERR \"Exit code: \$exitcode\\n\" if \$exitcode;
    return ! \$exitcode;
  }
}

######################
#  MAIN PROGRAM
######################
my \@cmd = (";
for(my $i=0; $i<=$#args; $i++)
{
    print SCRIPT "\"$args[$i]\"";
    print SCRIPT ", " unless($i==$#args);
}
print SCRIPT ");
safesystem(\@cmd);
";
}

sub generate_perl_script_depend()
{
    my ($script, $nodes, $ppn, $mem, $log, $pid, @args) = @_;
    
    open(SCRIPT, ">$script") or die "Can't create file $script! $!";

print SCRIPT "#!/usr/bin/perl
#PBS -q trad -d . -V  
#PBS -l nodes=$nodes:ppn=$ppn 
#PBS -l mem=${mem}g 
#PBS -l cput=1000:00:00  
#PBS -j oe 
#PBS -o $log 
#PBS -W \"depend=afterok:$pid\"
use strict;
######################
#  FUNCTIONS
######################
sub safesystem {
  print STDERR \"Executing: \@_\\n\";
  system(\@_);
  if (\$? == -1) {
      print STDERR \"Failed to execute: \@_\\n  \$!\\n\";
      exit(1);
  }
  elsif (\$? & 127) {
      printf STDERR \"Execution of: \@_\\n  died with signal %d, %s coredump\\n\",
          (\$? & 127),  (\$? & 128) ? 'with' : 'without';
      exit(1);
  }
  else {
    my \$exitcode = \$? >> 8;
    print STDERR \"Exit code: \$exitcode\\n\" if \$exitcode;
    return ! \$exitcode;
  }
}

######################
#  MAIN PROGRAM
######################
my \@cmd = (";
for(my $i=0; $i<=$#args; $i++)
{
    print SCRIPT "\"$args[$i]\"";
    print SCRIPT ", " unless($i==$#args);
}
print SCRIPT ");
safesystem(\@cmd);
";
}

sub generate_condor_xml()
{
    my ($script, $objective, $basename) = @_;   
    open(SCRIPT, ">$script") or die "Can't create file $script! $!";

print SCRIPT "<?xml version=\"1.0\" encoding=\"UTF-8\">
<configCONDOR>
<varNames dimension=\"6\">
  del stem syn ins sub shift
</varNames>
<objectiveFunction nIndex=\"1\">
  <executableFile> $objective </executableFile>
  <inputObjectiveFile> $basename.input </inputObjectiveFile>
  <outputObjectiveFile> $basename.output </outputObjectiveFile>
</objectiveFunction>
<startingPoint>
  1.0 1.0 1.0 1.0 1.0 1.0
</startingPoint>
<optimizationParameters
  rhostart=\"0.1\"
  rhoend=\"0.002\"
  timeToSleep=\"2\"
  maxIteration=\"30\"
/>
<dataFiles
        binaryDatabaseFile=\"$basename.bin\"
/>
<resultFile>
  $basename.res
</resultFile>
</configCONDOR>";

    close(SCRIPT);
}


sub create_MANY_config()
{
    my $_MANY_CONFIG;
    local @ARGV;
    ($_MANY_CONFIG, @ARGV) = @_;

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

my $typeok = 0;
$typeok = 1 if $_CONFIG_TYPE eq "MANY";   # MANY config file
$typeok = 1 if $_CONFIG_TYPE eq "DECODE"; # MANYdecode config file
$typeok = 1 if $_CONFIG_TYPE eq "BLEU";   # MANYbleu config file
if($typeok != 1)
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
close(MANYCFG);

}
