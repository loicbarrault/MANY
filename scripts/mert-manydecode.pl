#!/usr/bin/perl 
##-w 

# $Id$
# Usage:
# mert-manydecode.pl <foreign> <english> <decoder-executable> <decoder-config>
# For other options see below or run 'mert-many.pl --help'

    # Notes:
    # <foreign> and <english> should be raw text files, one sentence per line
    # <english> can be a prefix, in which case the files are <english>0, <english>1, etc. are used

    # Revision history

    # 5 Aug 2009  Handling with different reference length policies (shortest, average, closest) for BLEU 
    #             and case-sensistive/insensitive evaluation (Nicola Bertoldi)
    # 5 Jun 2008  Forked previous version to support new mert implementation.
    # 13 Feb 2007 Better handling of default values for lambda, now works with multiple
    #             models and lexicalized reordering
    # 11 Oct 2006 Handle different input types through parameter --inputype=[0|1]
    #             (0 for text, 1 for confusion network, default is 0) (Nicola Bertoldi)
    # 10 Oct 2006 Allow skip of filtering of phrase tables (--no-filter-phrase-table)
    #             useful if binary phrase tables are used (Nicola Bertoldi)
    # 28 Aug 2006 Use either closest or average or shortest (default) reference
    #             length as effective reference length
    #             Use either normalization or not (default) of texts (Nicola Bertoldi)
    # 31 Jul 2006 move gzip run*.out to avoid failure wit restartings
    #             adding default paths
    # 29 Jul 2006 run-filter, score-nbest and mert run on the queue (Nicola; Ondrej had to type it in again)
    # 28 Jul 2006 attempt at foolproof usage, strong checking of input validity, merged the parallel and nonparallel version (Ondrej Bojar)
    # 27 Jul 2006 adding the safesystem() function to handle with process failure
    # 22 Jul 2006 fixed a bug about handling relative path of configuration file (Nicola Bertoldi) 
    # 21 Jul 2006 adapted for Moses-in-parallel (Nicola Bertoldi) 
    # 18 Jul 2006 adapted for Moses and cleaned up (PK)
    # 21 Jan 2005 unified various versions, thorough cleanup (DWC)
    #             now indexing accumulated n-best list solely by feature vectors
    # 14 Dec 2004 reimplemented find_threshold_points in C (NMD)
    # 25 Oct 2004 Use either average or shortest (default) reference
    #             length as effective reference length (DWC)
    # 13 Oct 2004 Use alternative decoders (DWC)
    # Original version by Philipp Koehn

    use FindBin qw($Bin);
    use File::Basename;

    my $SCRIPTS_ROOTDIR = $Bin;
    $SCRIPTS_ROOTDIR =~ s/\/training$//;
    $SCRIPTS_ROOTDIR = $ENV{"SCRIPTS_ROOTDIR"} if defined($ENV{"SCRIPTS_ROOTDIR"});

    my $MANY_ROOTDIR = "./";

    # for each decoder parameter (_lm_weight, _null_penalty _word_penalty and priors),
    # there is a list of [ default value, lower bound, upper bound ]-triples. 
    # In most cases, only one triple is used

    # defaults for initial values and ranges are:
    my $default_triples = {
        # these basic models exist even if not specified, they are
        # not associated with any model file
        "lm" => [ [ 1.0, 0.0, 2.0 ] ],  # language model
        "word" => [ [ 0.0, -1.0, 1.0 ] ],  # word penalty (equiv word penalty)
        "null" => [ [ 0.0, -1.0, 1.0 ] ],  # null penalty
        "priors" => [ ] #prior probabilities
    };

    my $additional_triples = {
        # if more lambda parameters for the weights are needed
        # (due to additional tables) use the following values for them
};
    # the following models (given by shortname) use same triplet
    # for any number of lambdas, the number of the lambdas is determined
    # by the ini file
#my $additional_triples_loop = { map { ($_, 1) } qw/ d I / };

# many_config.xml file uses FULL names for lambdas, while this training script internally (and on the command line)
# uses ABBR names.
my $ABBR_FULL_MAP = "lm=lm-weight word=word-penalty null=null-penalty priors=priors";
my %ABBR2FULL = map {split/=/,$_,2} split /\s+/, $ABBR_FULL_MAP;
my %FULL2ABBR = map {my ($a, $b) = split/=/,$_,2; ($b, $a);} split /\s+/, $ABBR_FULL_MAP;

#foreach my $t (keys %FULL2ABBR){ print "FULL2ABBR{$t} -> $FULL2ABBR{$t}\n"; }

my $minimum_required_change_in_weights = 0.00001;
# stop if no lambda changes more than this

my $verbose = 0;
my $usage = 0; # request for --help
my $___WORKING_DIR = "mert-work";
my @___HYPS = (); # required, input text to combine
my $___REF_BASENAME = undef; # required, basename of files with references
my $___MANY = undef; # required, pathname to the MANY script (executable)
my $___MANY_CONFIG = undef; # required, pathname to startup config file (many.xml)
my $___N_BEST_LIST_SIZE = 300;
my $queue_flags = "-hard";  # extra parameters for parallelizer
      # the -l ws0ssmt is relevant only to JHU workshop
my $___NB_THREADS = undef; # if parallel, number of jobs to use (undef -> serial)
my $___DECODER_FLAGS = ""; # additional parametrs to pass to the decoder
my $___MANY_FLAGS = ""; # additional parameters to pass to MANY
my $___LAMBDA = undef; # string specifying the seed weights and boundaries of all lambdas
my $continue = 0; # should we try to continue from the last saved step?
my $skip_decoder = 0; # and should we skip the first decoder run (assuming we got interrupted during mert)
my $___PREDICTABLE_SEEDS = 0;


# Parameter for effective reference length when computing BLEU score
# Default is to use shortest reference
# Use "--shortest" to use shortest reference length
# Use "--average" to use average reference length
# Use "--closest" to use closest reference length
# Only one between --shortest, --average and --closest can be set
# If more than one choice the defualt (--shortest) is used
my $___SHORTEST = 0;
my $___AVERAGE = 0;
my $___CLOSEST = 0;

# Use "--nocase" to compute case-insensitive scores
my $___NOCASE = 0;

# Use "--nonorm" to non normalize translation before computing scores
my $___NONORM = 0;

# set 0 if input type is text, set 1 if input type is confusion network
my $___INPUTTYPE = 0; 

my $mertdir = undef; # path to new mert directory
my $mertargs = undef; # args to pass through to mert
my $qsubwrapper = undef;
my $scorer_config = "BLEU:1";
my $old_sge = 0; # assume sge<6.0
my $many_cmd = undef;
my $___MANY_CONFIG_BAK = undef; # backup pathname to startup ini file
my $efficient_scorenbest_flag = undef; # set to 1 to activate a time-efficient scoring of nbest lists
                                  # (this method is more memory-consumptive)
my $___ACTIVATE_FEATURES = undef; # comma-separated (or blank-separated) list of features to work on 
                                  # if undef work on all features
                                  # (others are fixed to the starting values)
my $prev_aggregate_nbl_size = -1; # number of previous step to consider when loading data (default =-1)
                                  # -1 means all previous, i.e. from iteration 1
                                  # 0 means no previous data, i.e. from actual iteration
                                  # 1 means 1 previous data , i.e. from the actual iteration and from the previous one
                                  # and so on 

my $starting_weights_from_xml = 1;
my $maximum_iterations = 25;

use strict;
use Getopt::Long;
GetOptions(
  "working-dir=s" => \$___WORKING_DIR,
  "hyp=s" => \@___HYPS,
  "refs=s" => \$___REF_BASENAME,
  "many=s" => \$___MANY,
  "config=s" => \$___MANY_CONFIG,
  "nbest=i" => \$___N_BEST_LIST_SIZE,
  "queue-flags=s" => \$queue_flags,
  "nb-threads=i" => \$___NB_THREADS,
  "decoder-flags=s" => \$___DECODER_FLAGS,
  "lambdas=s" => \$___LAMBDA,
  "continue" => \$continue,
  "skip-decoder" => \$skip_decoder,
  "shortest" => \$___SHORTEST,
  "average" => \$___AVERAGE,
  "closest" => \$___CLOSEST,
  "nocase" => \$___NOCASE,
  "nonorm" => \$___NONORM,
  "help" => \$usage,
  "verbose" => \$verbose,
  "mertdir=s" => \$mertdir,
  "mertargs=s" => \$mertargs,
  "rootdir=s" => \$SCRIPTS_ROOTDIR,
  "many-rootdir=s" => \$MANY_ROOTDIR,
  "qsubwrapper=s" => \$qsubwrapper, # allow to override the default location
  "many-cmd=s" => \$many_cmd, # allow to override the default location
  "old-sge" => \$old_sge, #passed to moses-parallel
  "predictable-seeds" => \$___PREDICTABLE_SEEDS, # allow (disallow) switch on/off reseeding of random restarts
  "efficient_scorenbest_flag" => \$efficient_scorenbest_flag, # activate a time-efficient scoring of nbest lists
  "activate-features=s" => \$___ACTIVATE_FEATURES, #comma-separated (or blank-separated) list of features to work on (others are fixed to the starting values)
  "prev-aggregate-nbestlist=i" => \$prev_aggregate_nbl_size, #number of previous step to consider when loading data (default =-1, i.e. all previous)
  "maximum-iterations=i" => \$maximum_iterations,
  "starting-weights-from-xml!" => \$starting_weights_from_xml,
  "sc-config=s" => \$scorer_config
) or exit(1);

# the 4 required parameters can be supplied on the command line directly
# or using the --options
if (scalar @ARGV >= 5) {
  # required parameters: many_script config_file references_basename input_file1 input_file2 ... input_fileN 
  $___REF_BASENAME = shift;
  $___MANY = shift;
  $___MANY_CONFIG = shift;
  push(@___HYPS, @ARGV);
}

print "$0: running on ".`hostname -f`."\n";
print "MANY : $___MANY\n" if defined($___MANY);
print "CONFIG : $___MANY_CONFIG\n" if defined($___MANY_CONFIG);
print "REFS : $___REF_BASENAME\n" if defined($___REF_BASENAME);
print "HYPS : @___HYPS\n" if @___HYPS > 0;

if ($usage || !defined $___REF_BASENAME || scalar @___HYPS < 2 || !defined $___MANY || !defined $___MANY_CONFIG) {
  print STDERR "usage: mert-manydecode.pl references_basename many_script config_file input_file1 input_file2 ... input_fileN
Options:
  --working-dir=mert-dir ... where all the files are created
  --nbest=100            ... how big nbestlist to generate
  --nb-threads=N         ... set this to anything greater than 1 to run MANY in multithreaded mode
  --decoder-flags=STRING ... extra parameters for the decoder
  --many-cmd=STR          ... use a different script instead of MANY
  --queue-flags=STRING   ... anything you wish to pass to qsub, eg.
                             '-l ws06osssmt=true'. The default is: '-hard'
                             To reset the parameters, please use 
                             --queue-flags=' ' (i.e. a space between the quotes).
  --lambdas=STRING       ... default values and ranges for lambdas, a
                             complex string such as
                             'd:1,0.5-1.5 lm:1,0.5-1.5 tm:0.3,0.25-0.75;0.2,0.25-0.75;0.2,0.25-0.75;0.3,0.25-0.75;0,-0.5-0.5 w:0,-0.5-0.5'
  --allow-unknown-lambda ... keep going even if someone supplies a new
                             lambda in the lambdas option (such as
                             'superbmodel:1,0-1'); optimize it, too
  --continue             ... continue from the last successful iteration
  --skip-decoder         ... skip the decoder run for the first time, assuming that we got interrupted during optimization
  --shortest --average --closest
                         ... Use shortest/average/closest reference length
                             as effective reference length (mutually exclusive)
  --nocase               ... Do not preserve case information; i.e.
                             case-insensitive evaluation (default is false).
  --nonorm               ... Do not use text normalization (flag is not active,
                             i.e. text is NOT normalized)
  --rootdir=STRING       ... where do helpers reside (if not given explicitly)
  --mertdir=STRING       ... path to new mert implementation
  --mertargs=STRING      ... extra args for mert, eg. to specify scorer
  --scorenbestcmd=STRING ... path to score-nbest.py
  --predictable-seeds    ... provide predictable seeds to mert so that random
                             restarts are the same on every run
  --efficient_scorenbest_flag ... time-efficient scoring of nbest lists
                                  (this method is more memory-consumptive)
  --activate-features=STRING  ... comma-separated list of features to optimize,
                                  others are fixed to the starting values
                                  default: optimize all features
                                  example: tm_0,tm_4,d_0
  --prev-aggregate-nbestlist=INT ... number of previous step to consider when loading data (default =-1)
                                    -1 means all previous, i.e. from iteration 1
                                     0 means no previous data, i.e. only the current iteration
                                     N means this and N previous iterations

  --maximum-iterations=ITERS ... Maximum number of iterations. Default: $maximum_iterations
  --starting-weights-from-xml ... use the weights given in MANY.xml file as
                                  the starting weights (and also as the fixed
                                  weights if --activate-features is used).
                                  default: yes (used to be 'no')
  --sc-config=STRING     ... extra option to specify multiscoring ex: BLEU:1,TER:1 => BLEU-TER/2.
";
  exit 1;
}

#add lambdas for priors
my $arrayref = $$default_triples{"priors"};
my $def_prob = 1.0/(scalar @___HYPS);
for(my $n=0; $n<scalar @___HYPS; $n++)
{
	push (@$arrayref, [ $def_prob, -1.0, 1.0 ]);    # prior probabilities
}
dump_triples($default_triples);

# Check validity of input parameters and set defaults if needed
print STDERR "Using SCRIPTS_ROOTDIR: $SCRIPTS_ROOTDIR\n";

# path of script for filtering phrase tables and running the decoder
$qsubwrapper="$SCRIPTS_ROOTDIR/generic/qsub-wrapper.pl" if !defined $qsubwrapper;

#$many_cmd = "$MANY_ROOTDIR/MANYdecode_template.pl" if !defined $many_cmd;
#die "Not executable: $many_cmd" if ! -x $many_cmd;

if (!defined $mertdir) {
  $mertdir = "$SCRIPTS_ROOTDIR/../mert";
  print STDERR "Assuming --mertdir=$mertdir\n";
}

my $mert_extract_cmd = "$mertdir/extractor";
my $mert_mert_cmd = "$mertdir/mert";

die "Not executable: $mert_extract_cmd" if ! -x $mert_extract_cmd;
die "Not executable: $mert_mert_cmd" if ! -x $mert_mert_cmd;

$mertargs = "" if !defined $mertargs;

my $scconfig = undef;
if ($mertargs =~ /\-\-scconfig\s+(.+?)(\s|$)/){
  $scconfig=$1;
  $scconfig =~ s/\,/ /g;
  $mertargs =~ s/\-\-scconfig\s+(.+?)(\s|$)//;
}

# handling reference lengh strategy
if (($___CLOSEST + $___AVERAGE + $___SHORTEST) > 1){
  die "You can specify just ONE reference length strategy (closest or shortest or average) not both\n";
}

if ($___SHORTEST){
  $scconfig .= " reflen:shortest";
}elsif ($___AVERAGE){
  $scconfig .= " reflen:average";
}elsif ($___CLOSEST){
  $scconfig .= " reflen:closest";
}

# handling case-insensitive flag
if ($___NOCASE) {
  $scconfig .= " case:false";
}else{
  $scconfig .= " case:true";
}
$scconfig =~ s/^\s+//;
$scconfig =~ s/\s+$//;
$scconfig =~ s/\s+/,/g;

$scconfig = "--scconfig $scconfig" if ($scconfig);

my $mert_extract_args=$mertargs;
$mert_extract_args .=" $scconfig";

my $mert_mert_args=$mertargs;
$mert_mert_args =~ s/\-+(binary|b)\b//;
$mert_mert_args .=" $scconfig";
if ($___ACTIVATE_FEATURES){ $mert_mert_args .=" -o \"$___ACTIVATE_FEATURES\""; }

#die "Not executable: $many_cmd" if defined $___NB_THREADS && ! -x $many_cmd;
die "Not executable: $qsubwrapper" if defined $___NB_THREADS && ! -x $qsubwrapper;
die "Not executable: $___MANY" if ! -x $___MANY;

#print "HYPS BEFORE : @___HYPS\n";
my @___HYPS_ABS = (); # required, input texts to combine (absolute)
foreach my $input (@___HYPS)
{
	#print "input = >$input< --> ";
	my $input_abs = ensure_full_path($input);
	die "File not found: $input (interpreted as $input_abs)." if ! -e $input_abs;
	print "Input (absolute path) = >$input_abs<\n";
	push(@___HYPS_ABS, $input_abs);
}
@___HYPS = @___HYPS_ABS;
#print "HYPS AFTER : >@___HYPS\n";

my $decoder_abs = ensure_full_path($___MANY);
die "File not found: $___MANY (interpreted as $decoder_abs)."
  if ! -x $decoder_abs;
$___MANY = $decoder_abs;

my $ref_abs = ensure_full_path($___REF_BASENAME);
# check if English dev set (reference translations) exist and store a list of all references
my @references;
if (-e $ref_abs) {
  push @references, $ref_abs;
}
else {
  # if multiple file, get a full list of the files
  #  my $part = 0;
  #  while (-e $ref_abs.".".$part) {
  #      push @references, $ref_abs.".".$part;
  #      $part++;
  #  }
  #  die("Reference translations not found: $___REF_BASENAME (interpreted as $ref_abs)") unless $part;
    my $ref = safebackticks(("find", "$ref_abs*.[0-9]", "-maxdepth", "0", "-follow"));
    @references = split(/\n/, $ref);
    chomp(@references);
}

my $config_abs = ensure_full_path($___MANY_CONFIG);
die "File not found: $___MANY_CONFIG (interpreted as $config_abs)."
  if ! -e $config_abs;
$___MANY_CONFIG = $config_abs;

# Option to pass to qsubwrapper and moses-parallel
my $pass_old_sge = $old_sge ? "-old-sge" : "";

# check validity of many_config.xml and collect number of models and lambdas per model
# need to make a copy of $extra_lambdas_for_model, scan_many_config spoils it
#my %copy_of_extra_lambdas_for_model = %$extra_lambdas_for_model;
my %used_triples = %{$default_triples};
scan_many_config($___MANY_CONFIG);

# Parse the lambda config string and convert it to a nice structure in the same format as $used_triples
if (defined $___LAMBDA) {
  my %specified_triples;
  # interpreting lambdas from command line
  foreach (split(/\s+/,$___LAMBDA)) {
      my ($name,$values) = split(/:/);
      die "Malformed setting: '$_', expected name:values\n" if !defined $name || !defined $values;
      foreach my $startminmax (split/;/,$values) {
	    if ($startminmax =~ /^(-?[\.\d]+),(-?[\.\d]+)-(-?[\.\d]+)$/) {
	        my $start = $1;
	        my $min = $2;
	      my $max = $3;
          push @{$specified_triples{$name}}, [$start, $min, $max];
	    }
	    else {
	      die "Malformed feature range definition: $name => $startminmax\n";
	    }
      } 
  }
  # sanity checks for specified lambda triples
  foreach my $name (keys %used_triples) {
      die "No lambdas specified for '$name', but ".($#{$used_triples{$name}}+1)." needed.\n"
	  unless defined($specified_triples{$name});
      die "Number of lambdas specified for '$name' (".($#{$specified_triples{$name}}+1).") does not match number needed (".($#{$used_triples{$name}}+1).")\n"
	  if (($#{$used_triples{$name}}) != ($#{$specified_triples{$name}}));
  }
  foreach my $name (keys %specified_triples) {
      die "Lambdas specified for '$name' ".(@{$specified_triples{$name}}).", but none needed.\n"
	  unless defined($used_triples{$name});
  }
  %used_triples = %specified_triples;
}

# as weights are normalized in the next steps (by cmert)
# normalize initial LAMBDAs, too
my $need_to_normalize = 1;

my @order_of_lambdas_from_decoder = ();
# this will store the labels of scores coming out of the decoder (and hence the order of lambdas coming out of mert)
# we will use the array to interpret the lambdas
# the array gets filled with labels only after first nbestlist was generated

#store current directory and create the working directory (if needed)
my $cwd = `pawd 2>/dev/null`; 
if(!$cwd){$cwd = `pwd`;}
chomp($cwd);

safesystem("mkdir -p $___WORKING_DIR") or die "Can't mkdir $___WORKING_DIR";

{
# open local scope

#chdir to the working directory
chdir($___WORKING_DIR) or die "Can't chdir to $___WORKING_DIR";

# fixed file names
my $mert_logfile = "mert.log";
my $weights_in_file = "init.opt";
my $weights_out_file = "weights.txt";

# set start run
my $start_run = 1;
my $bestpoint = undef;
my $devbleu = undef;

my $prev_feature_file = undef;
my $prev_score_file = undef;

if ($continue) {
  # getting the last finished step
  print STDERR "Trying to continue an interrupted optimization.\n";
  open IN, "finished_step.txt" or die "Failed to find the step number, failed to read finished_step.txt";
  my $step = <IN>;
  chomp $step;
  close IN;

  print STDERR "Last finished step is $step\n";

  # getting the first needed step
  my $firststep;
  if ($prev_aggregate_nbl_size==-1){
    $firststep=1;
  }
  else{
    $firststep=$step-$prev_aggregate_nbl_size+1;
    $firststep=($firststep>0)?$firststep:1;
  }

#checking if all needed data are available
  if ($firststep<=$step){
    print STDERR "First previous needed data index is $firststep\n";
    print STDERR "Checking whether all needed data (from step $firststep to step $step) are available\n";
    
    for (my $prevstep=$firststep; $prevstep<=$step;$prevstep++){
      print STDERR "Checking whether data of step $prevstep are available\n";
      if (! -e "run$prevstep.features.dat"){
	die "Can't start from step $step, because run$prevstep.features.dat was not found!";
      }else{
	if (defined $prev_feature_file){
	  $prev_feature_file = "${prev_feature_file},run$prevstep.features.dat";
	}
	else{
	  $prev_feature_file = "run$prevstep.features.dat";
	}
      }
      if (! -e "run$prevstep.scores.dat"){
	die "Can't start from step $step, because run$prevstep.scores.dat was not found!";
      }else{
	if (defined $prev_score_file){
	  $prev_score_file = "${prev_score_file},run$prevstep.scores.dat";
	}
	else{
	  $prev_score_file = "run$prevstep.scores.dat";
	}
      }
    }
    if (! -e "run$step.weights.txt"){
      die "Can't start from step $step, because run$step.weights.txt was not found!";
    }
    if (! -e "run$step.$mert_logfile"){
      die "Can't start from step $step, because run$step.$mert_logfile was not found!";
    }
    if (! -e "run$step.best$___N_BEST_LIST_SIZE.out.gz"){
      die "Can't start from step $step, because run$step.best$___N_BEST_LIST_SIZE.out.gz was not found!";
    }
    print STDERR "All needed data are available\n";

    print STDERR "Loading information from last step ($step)\n";
    open(IN,"run$step.$mert_logfile") or die "Can't open run$step.$mert_logfile";
    while (<IN>) {
      if (/Best point:\s*([\s\d\.\-e]+?)\s*=> ([\-\d\.]+)/) 
      {
		$bestpoint = $1;
		$devbleu = $2;
		last;
      }
    }
    close IN;
    die "Failed to parse mert.log, missed Best point there."
      if !defined $bestpoint || !defined $devbleu;
    print "($step) BEST at $step $bestpoint => $devbleu at ".`date`;
    my @newweights = split /\s+/, $bestpoint;
    
    print STDERR "Reading last cached lambda values (result from step $step)\n";
    @order_of_lambdas_from_decoder = get_order_of_scores_from_nbestlist("gunzip -c < run$step.best$___N_BEST_LIST_SIZE.out.gz |");
    
    # update my cache of lambda values
    store_new_lambda_values(\%used_triples, \@order_of_lambdas_from_decoder, \@newweights);
  }
  else{
    print STDERR "No previous data are needed\n";
  }

  $start_run = $step +1;
}
else{ print STDERR "Starting a brand new optimization.\n";}

$___MANY_CONFIG_BAK = $___MANY_CONFIG;

my $PARAMETERS;
#$PARAMETERS = $___DECODER_FLAGS . " -config $___MANY_CONFIG -inputtype $___INPUTTYPE";
$PARAMETERS = $___DECODER_FLAGS;

my $run=$start_run-1;
my $oldallsorted = undef;
my $allsorted = undef;

my $cmd;
# features and scores from the last run.
my $nbest_file=undef;

while(1) {
  $run++;
  if ($maximum_iterations && $run > $maximum_iterations) {
      print "Maximum number of iterations exceeded - stopping\n";
      last;
  }
  # run MANYdecode with option to output nbestlists
  # the end result should be (1) @NBEST_LIST, a list of lists; (2) @SCORE, a list of lists of lists

  print "run $run start at ".`date`;

  # In case something dies later, we might wish to have a copy
  create_many_config($___MANY_CONFIG, "./run$run.many.config.xml", \%used_triples, $run, (defined$devbleu?$devbleu:"--not-estimated--"));

  # skip if the user wanted
  if (!$skip_decoder) {
      print "($run) run MANY to produce n-best lists\n";
      $nbest_file = run_many(\%used_triples, $PARAMETERS, $run, \@order_of_lambdas_from_decoder, $need_to_normalize);
      safesystem("gzip -f $nbest_file") or die "Failed to gzip run*out";
      $nbest_file = $nbest_file.".gz";
  }
  else {
      $nbest_file="run$run.best$___N_BEST_LIST_SIZE.out.gz";
      print "skipped decoder run $run\n";
      if (0 == scalar @order_of_lambdas_from_decoder) {
        @order_of_lambdas_from_decoder = get_order_of_scores_from_nbestlist("gunzip -dc $nbest_file | head -1 |");
      }
      $skip_decoder = 0;
      $need_to_normalize = 0;
  }

  # extract score statistics and features from the nbest lists
  print STDERR "Scoring the nbestlist.\n";

  my $base_feature_file = "features.dat";
  my $base_score_file = "scores.dat";
  my $feature_file = "run$run.${base_feature_file}";
  my $score_file = "run$run.${base_score_file}";

############OLD
#$cmd = "$mert_extract_cmd $mert_extract_args --scfile $score_file --ffile $feature_file -r ".join(",", @references)." -n $nbest_file";
#
#  if (defined $___NB_THREADS) {
#    safesystem("$qsubwrapper $pass_old_sge -command='$cmd' -queue-parameter=\"$queue_flags\" -stdout=extract.out -stderr=extract.err" )
#      or die "Failed to submit extraction to queue (via $qsubwrapper)";
#  } else {
#    safesystem("$cmd > extract.out 2> extract.err") or die "Failed to do extraction of statistics.";
#  }
############END OLD

# # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # 
  my $cmd = "";
  my $scorer_name;
  my $scorer_weight;
  $scorer_config=~s/ //g;
  my @lists_scorer_config=split(",",$scorer_config);
  $mert_mert_args=$mert_mert_args." --sctype MERGE ";
  my $scorer_config_spec;
  # launch all extractors 
  foreach $scorer_config_spec(@lists_scorer_config)
  {
    my @lists_scorer_config_spec=split(":",$scorer_config_spec);
    $scorer_name=$lists_scorer_config_spec[0];
    $scorer_weight=$lists_scorer_config_spec[1];
    $cmd = "$mert_extract_cmd $mert_extract_args --scfile $score_file.$scorer_name --ffile $feature_file.$scorer_name --sctype $scorer_name -r ".join(",", @references)." -n $nbest_file";
    
    #&submit_or_exec($cmd,"extract.out.$scorer_name","extract.err.$scorer_name");
    safesystem("$cmd > extract.out.$scorer_name 2> extract.err.$scorer_name") or die "ERROR: Failed to run '$cmd'.";

  }
  my @scorer_content;
  my $fileIncrement=0;
  #prepare init file for merging metrics
  open(FILE,">merge.init") || die ("File creation ERROR : merge.init");
  foreach $scorer_config_spec(@lists_scorer_config)
  {
    my @lists_scorer_config_spec=split(":",$scorer_config_spec);
    $scorer_name=$lists_scorer_config_spec[0];
    $scorer_weight=$lists_scorer_config_spec[1];
    print FILE "$scorer_name $scorer_weight $score_file.$scorer_name $feature_file.$scorer_name\n";
    my @tmp_content=`/bin/cat $score_file.$scorer_name`;
    $scorer_content[$fileIncrement] = [ @tmp_content ];
    if ($fileIncrement==0)
    {
	`/bin/cp $feature_file.$scorer_name $feature_file`;
    }
    $fileIncrement++;
  }
  close(FILE);
    # print STDERR "ON  VA RASSEMBLER dans $score_file\n";
  open(SCOREFILE,">$score_file") || die ("File creation ERROR : $score_file");
  my $newFileIncrement=0;
  my $contentIncrement=0;
  my $contentSize=scalar(@{$scorer_content[0]});
  while ($contentIncrement< $contentSize)
  {
      my $line="";
      $newFileIncrement=0;
      while($newFileIncrement< $fileIncrement)
      {
	 if (rindex($scorer_content[$newFileIncrement][$contentIncrement],"BEGIN")<0)
	 {
	    $line=$line." ".$scorer_content[$newFileIncrement][$contentIncrement];
	    chomp($line);
	 }
	 else
	 {
	    my @split_line_input=split(" ",$scorer_content[$newFileIncrement][$contentIncrement]);
	    my @split_line=split(" ",$line);
	    if (scalar(@split_line)>0)
	    {
		$split_line_input[3]=$split_line[3]+$split_line_input[3];
	    }
	    $line=$split_line_input[0]." ".$split_line_input[1]." ".$split_line_input[2]." ".$split_line_input[3]." MERGE";
	 }
	 $newFileIncrement++;
      }
      $line=~s/^[ ]+//g;
      $line=~s/[ ]+$//g;
      $line=~s/[ ]+/ /g;
      print SCOREFILE $line."\n";
      $contentIncrement++;
  }
  close(SCOREFILE);

# # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # 


  # Create the initial weights file for mert, in init.opt
  # mert reads in the file init.opt containing the current
  # values of lambda.

  # We need to prepare the files and **the order of the lambdas must
  # correspond to the order @order_of_lambdas_from_decoder

  my @MIN = ();   # lower bounds
  my @MAX = ();   # upper bounds
  my @CURR = ();  # the starting values
  my @NAME = ();  # to which model does the lambda belong
  
  my %visited = ();
  foreach my $name (@order_of_lambdas_from_decoder) {
  		if (!defined $visited{$name}) {
        	$visited{$name} = 0;
    	} else {
        	$visited{$name}++;
  		}
    
  		my ($val, $min, $max) = @{$used_triples{$name}->[$visited{$name}]};
  		print STDERR "$val, $min, $max\n"; 
		push @CURR, $val;
      	push @MIN, $min;
      	push @MAX, $max;
      	push @NAME, $name;        
  }

  open(OUT,"> $weights_in_file") or die "Can't write $weights_in_file (WD now $___WORKING_DIR)";
  print OUT join(" ", @CURR)."\n";
  close(OUT);
  print join(" ", @NAME)."\n";
  
  # make a backup copy labelled with this run number
  safesystem("\\cp -f $weights_in_file run$run.$weights_in_file") or die;

  my $DIM = scalar(@CURR); # number of lambdas

  # run mert
  $cmd = "$mert_mert_cmd -d $DIM $mert_mert_args -n 20";
  if ($___PREDICTABLE_SEEDS) {
      my $seed = $run * 1000;
      $cmd = $cmd." -r $seed";
  }

  if (defined $prev_feature_file) {
    $cmd = $cmd." --ffile $prev_feature_file,$feature_file";
  }
  else {
    $cmd = $cmd." --ffile $feature_file";
  }
  if (defined $prev_score_file) {
    $cmd = $cmd." --scfile $prev_score_file,$score_file";
  }
  else {
    $cmd = $cmd." --scfile $score_file";
  }

  $cmd = $cmd." --ifile run$run.$weights_in_file";

  if (defined $___NB_THREADS) {
    safesystem("$qsubwrapper $pass_old_sge -command='$cmd' -stderr=$mert_logfile -queue-parameter=\"$queue_flags\"") or die "Failed to start mert (via qsubwrapper $qsubwrapper)";
  } else {
    safesystem("$cmd 2> $mert_logfile") or die "Failed to run mert";
  }
  die "Optimization failed, file $weights_out_file does not exist or is empty"
    if ! -s $weights_out_file;


 # backup copies
 ##########OLD
 #safesystem ("\\cp -f extract.err run$run.extract.err") or die;
 #safesystem ("\\cp -f extract.out run$run.extract.out") or die;
 ###########END OLD
  foreach my $extractFiles(`/bin/ls extract.*`)
  {
    chomp $extractFiles;
    safesystem ("\\cp -f $extractFiles run$run.$extractFiles") or die;
  }

  safesystem ("\\cp -f $mert_logfile run$run.$mert_logfile") or die;
  safesystem ("touch $mert_logfile run$run.$mert_logfile") or die;
  safesystem ("\\cp -f $weights_out_file run$run.$weights_out_file") or die; # this one is needed for restarts, too

  print "run $run end at ".`date`;

  $bestpoint = undef;
  $devbleu = undef;
  open(IN,"run$run.$mert_logfile") or die "Can't open run$run.$mert_logfile";
  while (<IN>) {
    if (/Best point:\s*([\s\d\.\-e]+?)\s*=> ([\-\d\.]+)/) {
      $bestpoint = $1;
      $devbleu = $2;
      last;
    }
  }
  close IN;
  die "Failed to parse mert.log, missed Best point there."
    if !defined $bestpoint || !defined $devbleu;
  print "($run) BEST at $run: $bestpoint => $devbleu at ".`date`;

  my @newweights = split /\s+/, $bestpoint;

  # update my cache of lambda values
  store_new_lambda_values(\%used_triples, \@order_of_lambdas_from_decoder, \@newweights);

  # additional stopping criterion: weights have not changed
  my $shouldstop = 1;
  for(my $i=0; $i<@CURR; $i++) {
    die "Lost weight! mert reported fewer weights (@newweights) than we gave it (@CURR)"
 		if !defined $newweights[$i];
    if (abs($CURR[$i] - $newweights[$i]) >= $minimum_required_change_in_weights) {
    	$shouldstop = 0;
    	last;
    }
  }

  open F, "> finished_step.txt" or die "Can't mark finished step";
  print F $run."\n";
  close F;

  if ($shouldstop) {
    print STDERR "None of the weights changed more than $minimum_required_change_in_weights. Stopping.\n";
    last;
  }

  my $firstrun;
  if ($prev_aggregate_nbl_size==-1){
    $firstrun=1;
  }
  else{
    $firstrun=$run-$prev_aggregate_nbl_size+1;
    $firstrun=($firstrun>0)?$firstrun:1;
  }
  print "loading data from $firstrun to $run (prev_aggregate_nbl_size=$prev_aggregate_nbl_size)\n";
  $prev_feature_file = undef;
  $prev_score_file = undef;
  for (my $i=$firstrun;$i<=$run;$i++){ 
    if (defined $prev_feature_file){
      $prev_feature_file = "${prev_feature_file},run${i}.${base_feature_file}";
    }
    else{
      $prev_feature_file = "run${i}.${base_feature_file}";
    }
    if (defined $prev_score_file){
      $prev_score_file = "${prev_score_file},run${i}.${base_score_file}";
    }
    else{
      $prev_score_file = "run${i}.${base_score_file}";
    }
  }
  print "loading data from $prev_feature_file\n" if defined($prev_feature_file);
  print "loading data from $prev_score_file\n" if defined($prev_score_file);
}# end while(1)

print "Training finished at ".`date`;

if (defined $allsorted){ safesystem ("\\rm -f $allsorted") or die; };

safesystem("\\cp -f $weights_in_file run$run.$weights_in_file") or die;
safesystem("\\cp -f $mert_logfile run$run.$mert_logfile") or die;

open(DW, "$weights_in_file") or die;
my $w = <DW>;
my @dweights = split(/\s+/, $w);
close(DW);

my $str = "lm-weight:$dweights[0] word-penalty:$dweights[1] null-penalty:$dweights[2] priors:";
for(my $i=3; $i<=$#dweights; $i++)
{
    $str .= $dweights[$i];
    $str .= "#" unless($i == $#dweights);
}

open(DW, ">FINAL.$weights_out_file") or die "Can't create tuned weights file FINAL.$weights_out_file! $!";
print DW "$str";
close(DW);

create_many_config($___MANY_CONFIG_BAK, "./FINAL.many.config.xml", \%used_triples, $run, $devbleu);

# just to be sure that we have the really last finished step marked
open F, "> finished_step.txt" or die "Can't mark finished step";
print F $run."\n";
close F;


#chdir back to the original directory # useless, just to remind we were not there
chdir($cwd);

} # end of local scope



#################################
#  STORE_NEW_LAMBDA_VALUES
################################
sub store_new_lambda_values {
  # given new lambda values (in given order), replace the 'val' element in our triples
  my $triples = shift;
  my $names = shift;
  my $values = shift;

  my %idx = ();
  foreach my $i (0..scalar(@$values)-1) {
    my $name = $names->[$i];
    die "Missed name for lambda $values->[$i] (in @$values; names: @$names)"
      if !defined $name;
    if (!defined $idx{$name}) {
      $idx{$name} = 0;
    } else {
      $idx{$name}++;
    }
    die "We did not optimize '$name', but moses returned it back to us"
      if !defined $triples->{$name};
    die "Moses gave us too many lambdas for '$name', we had ".scalar(@{$triples->{$name}})
      ." but we got at least ".$idx{$name}+1
      if !defined $triples->{$name}->[$idx{$name}];

    # set the corresponding field in triples
    # print STDERR "Storing $i-th score as $name: $idx{$name}: $values->[$i]\n";
    $triples->{$name}->[$idx{$name}]->[0] = $values->[$i];
  }
}

sub dump_triples {
  my $triples = shift;

  foreach my $name (keys %$triples) {
    foreach my $triple (@{$triples->{$name}}) {
      my ($val, $min, $max) = @$triple;
      print STDERR "Triples:  $name\t$val\t$min\t$max\t\t($triple)\n";
    }
  }
}

sub run_many {
    my ($triples, $parameters, $run, $output_order_of_lambdas, $need_to_normalize) = @_;
    my $filename_template = "run%d.best$___N_BEST_LIST_SIZE.out";
    my $filename = sprintf($filename_template, $run);
    
    print "mert decoder-params = $parameters\n";
    # prepare the decoder config:
    my $many_config = "";
    my @vals = ();
    foreach my $name (keys %$triples) {
      $many_config .= "--$ABBR2FULL{$name} ";
      foreach my $triple (@{$triples->{$name}}) {
        my ($val, $min, $max) = @$triple;
        $many_config .= "%.6f ";
        push @vals, $val;
      }
    }
    if ($need_to_normalize) {
      print STDERR "Normalizing lambdas: @vals\n";
      my $totlambda=0;
      grep($totlambda+=abs($_),@vals);
      grep($_/=$totlambda,@vals);
      print STDERR "Normalized lambdas: @vals\n";
    }
    print STDERR "MANY_CFG = $many_config\n";
    print STDERR "     values = @vals\n";
    $many_config = sprintf($many_config, @vals);
    print "many_config = $many_config\n";

    # run MANY
	my $nbest_cmd = "--nbest-file $filename --nbest-size $___N_BEST_LIST_SIZE -nbest-format MOSES";
    my $many_cmd = "$___MANY --output run$run.output.many ";

	foreach my $hyp (@___HYPS)
	{
		$many_cmd .= "--hyp $hyp ";
	}

	if(defined $___NB_THREADS){
		$many_cmd .= " $many_config $parameters $nbest_cmd --config $___MANY_CONFIG --multithread $___NB_THREADS > run$run.out";
	}
	else {
		$many_cmd .= " $many_config $parameters $nbest_cmd --config $___MANY_CONFIG > run$run.out";
	}
	safesystem($many_cmd) or die "MANY died. Config was $many_config \n";

    if (0 == scalar @$output_order_of_lambdas) {
      # we have to peek at the nbestlist
      @$output_order_of_lambdas = get_order_of_scores_from_nbestlist($filename);
    } 
      # we have checked the nbestlist already, we trust the order of output scores does not change
      return $filename;
}

sub get_order_of_scores_from_nbestlist {
  # read the first line and interpret the ||| label: num num num label2: num ||| column in nbestlist
  # return the score labels in order
  my $fname_or_source = shift;
  print STDERR "Peeking at the beginning of nbestlist to get order of scores: $fname_or_source\n";
  open IN, $fname_or_source or die "Failed to get order of scores from nbestlist '$fname_or_source'";
  my $line = <IN>;
  close IN;
  die "Line empty in nbestlist '$fname_or_source'" if !defined $line;
  my ($sent, $hypo, $scores, $total) = split /\|\|\|/, $line;
  $scores =~ s/^\s*|\s*$//g;
  die "No scores in line: $line" if $scores eq "";

  my @order = ();
  my $label = undef;
  foreach my $tok (split /\s+/, $scores) {
    if ($tok =~ /^([a-z][0-9a-z]*):/i) {
      $label = $1;
    } elsif ($tok =~ /^-?[-0-9.e]+$/) {
      # a score found, remember it
      die "Found a score but no label before it! Bad nbestlist '$fname_or_source'!"
        if !defined $label;
      push @order, $label;
    } else {
      die "Not a label, not a score '$tok'. Failed to parse the scores string: '$scores' of nbestlist '$fname_or_source'";
    }
  }
  print STDERR "The decoder returns the scores in this order: @order\n";
  return @order;
}

sub create_many_config {

	my $infn = shift; # source config
    my $outfn = shift; # where to save the config
    my $triples = shift; # the lambdas we should write
    my $iteration = shift;  # just for verbosity
    my $bleu_achieved = shift; # just for verbosity

	my %P; # the hash of all parameters we wish to override
	
	# first convert the command line parameters to the hash
    { # ensure local scope of vars
		my $parameter=undef;
		print "Parsing --decoder-flags: |$___DECODER_FLAGS|\n";
		$___DECODER_FLAGS =~ s/^\s*|\s*$//;
		$___DECODER_FLAGS =~ s/\s+/ /;
		foreach (split(/ /,$___DECODER_FLAGS)) 
        {
			if (/^\-([^\d].*)$/) 
            {
				$parameter = $1;
				$parameter = $ABBR2FULL{$parameter} if defined($ABBR2FULL{$parameter});
			}
			else 
            {
				die "Found value with no -paramname before it: $_" if !defined $parameter;
				push @{$P{$parameter}},$_;
                print "$parameter -> $_\n";
		        $parameter=undef;
			}
		}
    } #end local scope
    
    # Convert weights to elements in P
    foreach my $abbr (keys %$triples) {
      # First delete all weights params from the input, in short or long-named version
      delete($P{$abbr});
      delete($P{$ABBR2FULL{$abbr}});
      # Then feed P with the current values
      foreach my $feature (@{$used_triples{$abbr}}) {
        my ($val, $min, $max) = @$feature;
        my $name = defined $ABBR2FULL{$abbr} ? $ABBR2FULL{$abbr} : $abbr;
        push @{$P{$name}}, $val;
      }
    }
    
    # create new many.config.xml decoder config file by cloning and overriding the original one
	open(INI,$infn) or die "Can't read $infn";
	delete($P{"config"}); # never output 
	print "Saving new config to: $outfn\n";
    open(OUT,"> $outfn") or die "Can't write $outfn";
    print OUT "# MERT optimized configuration\n";
    print OUT "# decoder $___MANY\n";
    print OUT "# BLEU $bleu_achieved on dev $___REF_BASENAME\n";
    print OUT "# We were before running iteration $iteration\n";
    print OUT "# finished ".`date`;
    my $line = <INI>;
    while(1) {
		last unless $line;
        #chomp $line;
		# skip until hit <property 
		if ($line !~ /^<property name=\"(.+)\"\s+value=\".+\"/) { 
	    	print OUT $line; # if $line =~ /^\#/ || $line =~ /^\s+$/;
	    	#print STDERR $line; # if $line =~ /^\#/ || $line =~ /^\s+$/;
		}
		else {
			# parameter name
			my $parameter = $1;
			$parameter = $ABBR2FULL{$parameter} if defined($ABBR2FULL{$parameter});
			
			# change parameter, if new values
			if (defined($P{$parameter})) {
				# write new values
				print OUT '<property name="'.$parameter.'" value="'."@{$P{$parameter}}".'"/>'."\n";
				#print STDERR '<property name="'.$parameter.'" value="'."@{$P{$parameter}}".'"/>'."\n";
				delete($P{$parameter});
			}
			else # unchanged parameter, write old
			{
				print OUT $line;
				#print STDERR $line;
			}
		}
	    $line = <INI>;
    }
    
    close(INI);
    close(OUT);
    print STDERR "Saved: $outfn\n";	
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

sub scan_many_config {
  my $xml = shift;
  my $xmlshortname = $xml; $xmlshortname =~ s/^.*\///; 
  # for error reporting
  # we get a pre-filled counts, because some lambdas are always needed (word penalty, for instance)
  # as we walk though the ini file, we record how many extra lambdas do we need
  # and finally, we report it

  # by default, 1 lambda per weightname, but sometimes, several are needed
  my %lambda_count_for_weight = (
    "priors" => scalar @___HYPS
  );

  my $config_weights;
  # to collect all weight values from many_config.xml
  #   $config_weights->{shortname}  is a reference to array of features
  
  open XML, $xml or die "Can't read $xml";
  my $nr = 0;
  my $error = 0;
  #my %defined_files;
  while (<XML>) {
    $nr++;
    chomp;
    next if /^\s*#/; # skip comments
    next if /^\s*$/; # skip blank lines
    
    if(/^<property name=\"(.+)\"\s+value=\"(.+)\"/) {
        my $weightname = $1;
        my $weightval = $2;
        my @tab;
        if( defined($FULL2ABBR{$weightname}) ){
        	
        	print "---> scan_config found LAMBDA : $weightname : $weightval\n";
            $config_weights->{$FULL2ABBR{$weightname}} = [] if ! defined $config_weights->{$FULL2ABBR{$weightname}};
        	
        	#check how many lambdas are needed
        	my $lambdacount = $lambda_count_for_weight{$FULL2ABBR{$weightname}};
			my @tab = split(/\s+/, $weightval);
			my $tabsize = scalar @tab;
        	if(defined $lambdacount && $lambdacount != $tabsize)
        	{
        		die "Found $tabsize lambda for $weightname, $lambdacount was needed!";
        	}
        	
        	#$config_weights{$FULL2ABBR{$weightname}} = $weightval;
            push @{$config_weights->{$FULL2ABBR{$weightname}}}, @tab;
        }
    }
  }
  die "$xmlshortname: File was empty!" if !$nr;
  close XML;
  
  
#  foreach my $k (sort keys %{$config_weights})
#  {
#	for(my $kkk=0; $kkk<scalar @{$config_weights->{$k}}; $kkk++)
#	{
#	   print " config_weight{$k}->[$kkk] : $$config_weights{$k}->[$kkk]  \n";
#	}
#  } 
  
  
  # check the weights provided in the xml file and plug them into the triples
  # if --starting-weights-from-xml
  foreach my $weightname (keys %used_triples) {
  	if (!defined $config_weights->{$weightname}) {
      print STDERR "$xmlshortname:Model requires weights '$weightname' but none were found in the xml file.\n";
      $error = 1;
      next;
    }
    my $thesetriplets = $used_triples{$weightname};
    my $theseconfig_weights = $config_weights->{$weightname};
    
    if ($starting_weights_from_xml) {
      # copy weights from many_config.xml to the starting value of used_triplets
       for (my $i=0; $i < @$theseconfig_weights; $i++) {
        $thesetriplets->[$i]->[0] = $theseconfig_weights->[$i];
      }
    }
  }

  exit(1) if $error;
  #return (\%defined_files);
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

