#!/usr/bin/perl

use strict;
use Getopt::Long;
use File::Basename;

my $_SC=undef;
my $_VERBOSE=0;
my $_HELP;

my %_CONFIG = (	
'score' => \$_SC,
'verbose' => \$_VERBOSE,
'help' => \$_HELP
); 

########################
######################## FUNCTIONS DEFINITION
my $usage = "add-ter-id.pl input_filename output_dir \
--score|--sc score : generate also score file with specified score \ 
--verbose|--v       : verbose \ 
--help|h            : print this help and exit \n";

########################
######################## Parsing parameters with GetOptions
$_HELP = 1 unless GetOptions(\%_CONFIG, 'score|sc=f', 'verbose|v', 'help|h');

die "Not enough arguments\n$usage" unless(scalar @ARGV == 2);

my $input = $ARGV[0];
my $outdir = $ARGV[1];
chomp $input;
my $basename = basename($input); 
print STDERR "Preparing input : $input, target is $outdir/$basename.id\n" if $_VERBOSE;

open(FROM, "$input") or die "Can't open $input\n";
my @lines = <FROM>;
close(FROM);

open(ID, ">$outdir/$basename.id") or die "Can't create $outdir/$basename.id\n";
open(SC_ID, ">$outdir/$basename.id.sc") or die "Can't create $outdir/$basename.id.sc\n" if(defined $_SC);
my $nbl = 0;
foreach my $line (@lines)
{
    my $sc_line = $line;
    $line =~ s/$/ ([set][doc.00][$nbl])/;	
    print ID $line;

    if(defined $_SC) {
        $sc_line =~ s/[^ \n]+/$_SC/g;
        $sc_line =~ s/$/ ([set][doc.00][$nbl])/;	
        print SC_ID $sc_line ;
    }
    $nbl++;
}
close(ID);
close(SC_ID);



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

