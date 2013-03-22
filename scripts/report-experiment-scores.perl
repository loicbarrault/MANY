#!/usr/bin/perl -w

# $Id: report-experiment-scores.perl 407 2008-11-10 14:43:31Z philipp $

use strict;

my $email;

my %TYPE;
$TYPE{"nist-bleu"}   = "BLEU";
$TYPE{"multi-bleu"}  = "BLEU";
$TYPE{"nist-bleu-c"} = "BLEU-c";
$TYPE{"multi-bleu-c"}= "BLEU-c";
$TYPE{"ibm-bleu"}    = "IBM";
$TYPE{"ibm-bleu-c"}  = "IBM-c";

my %SCORE;
my %AVERAGE;
foreach (@ARGV) {
    if (/^email='(\S+)'/) {
	    $email = $1;
    }
}
foreach (@ARGV) {
    if (/^set=(\S+),type=(\S+),file=(\S+)$/) {
	&process($1,$2,$3);
    }
}
foreach my $set (keys %SCORE) {
    my $score = $SCORE{$set};
    chop($score);
    print "$set: $score\n";
}
if ((scalar keys %SCORE) > 1) {
    print  "avg:";
    my $first = 1;
    foreach my $type (keys %AVERAGE) {
      print " ;" unless $first; $first = 0;
      printf " %.02f $TYPE{$type}",$AVERAGE{$type}/(scalar keys %SCORE);
    }
    print  "\n";
}

sub process {
    my ($set,$type,$file) = @_;
    $SCORE{$set} .= "; " if defined($SCORE{$set});
    if (! -e $file) {
	    print STDERR "ERROR (score $type for set $set): file '$file' does not exist!\n";
    }
    elsif ($type eq 'nist-bleu' || $type eq 'nist-bleu-c') {
	    $SCORE{$set} .= &extract_nist_bleu($file,$type)." ";
    }
    elsif ($type eq 'ibm-bleu' || $type eq 'ibm-bleu-c') {
	    $SCORE{$set} .= &extract_ibm_bleu($file,$type)." ";
    }
    elsif ($type eq 'multi-bleu' || $type eq 'multi-bleu-c') {
	    $SCORE{$set} .= &extract_multi_bleu($file,$type)." ";
    }
    elsif ($type eq 'score-rb' || $type eq 'score-rb-nc') {
        $SCORE{$set} .= &extract_score_rb($file,$type)." ";
    }
    else
    {
        print STDERR "ERROR (unknown type $type\n";
    }
}

sub extract_nist_bleu {
    my ($file,$type) = @_;
    my ($bleu,$ratio);
    foreach (`cat $file`) {
	$bleu = $1*100 if /BLEU score = (\S+)/;
	$ratio = int(1000*$1)/1000 if /length ratio: (\S+)/;
    }
    if (!defined($bleu)) {
	print STDERR "ERROR (extract_nist_bleu): could not find BLEU score in file '$file'\n";
	return "";
    }
    my $output = sprintf("%.02f ",$bleu);
    $output .= sprintf("(%.03f) ",$ratio) if $ratio;

    $AVERAGE{$type} += $bleu;

    return $output.$TYPE{$type};
}

sub extract_ibm_bleu {
    my ($file,$type) = @_;
    my ($bleu,$ratio);
    foreach (`cat $file`) {
	$bleu = $1*100 if /BLEUr\dn4c?,(\S+)/;
	$ratio = int(1000*(1/$1))/1000 if /Ref2SysLen,(\S+)/;
    }
    if (!$bleu) {
	print STDERR "ERROR (extract_ibm_bleu): could not find BLEU score in file '$file'\n";
	return "";
    }
    my $output = sprintf("%.02f ",$bleu);
    $output .= sprintf("(%.03f) ",$ratio) if $ratio;

    $AVERAGE{$type} += $bleu;

    return $output.$TYPE{$type};
}

sub extract_multi_bleu {
    my ($file,$type) = @_;
    my ($bleu,$ratio);
    foreach (`cat $file`) {
	$bleu = $1 if /BLEU = (\S+), /;
	$ratio = $1 if / ration?=(\S+)\)/;
    }
    my $output = sprintf("%.02f ",$bleu);
    $output .= sprintf("(%.03f) ",$ratio) if $ratio;

    $AVERAGE{"multi-bleu"} += $bleu;

    return $output.$TYPE{$type};
}

sub extract_score_rb {
    my ($file,$type) = @_;
    my $output = "";
    my $ok = 0;
    my @header = ('BLEU', 'NIST', 'TER', 'METEOR',  'Precision', 'Recall', 'Length');
    foreach (`cat $file`) {
        #BLEU  NIST  TER   METEO Preci Recal Leng
        #8.95  3.27  83.62 39.76 50.67 52.49 1.15
        chomp;
        $_ =~ s/^\s+|\s+$//;
        my @scores = split(/\s+/, $_);
        
        #print STDERR "$_ : scalar = ".scalar @scores."\n";
        #Evaluating /lium/trad2/barrault/ems/wmt11/syscomb-FR/evaluation/newssyscombtest2011.output.1 34.51  8.11 50.54 60.80 67.10 66.05 1.01
        #if( scalar @scores == 7 )
        #{
        for(my $i=0; $i<=$#scores-2; $i++)
        {
            $output .= " $header[$i]:$scores[$i+2]"; 
        }
        #$ok = 1;
        #}
    }

    return $output;
}


