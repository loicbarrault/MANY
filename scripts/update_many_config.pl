#!/usr/bin/perl -w 

use strict;

my $usage = "create_many_config.pl input.config output.config newparameters"; 
die "$usage\n" unless $#ARGV==2; 
my ($infn, $outfn, $newparameters) = @ARGV;

my %P; # the hash of all parameters we wish to override
# first convert the command line parameters to the hash
{ # ensure local scope of vars
    my $parameter=undef;
    my $value=undef;
    print "Parsing new parameters |$newparameters|\n";
    $newparameters =~ s/^\s*|\s*$//;
    #$newparameters =~ s/\s+/ /;
    foreach (split(/\s+/,$newparameters)) {
        if (/^(.+):(.+)$/) {
            $parameter = $1;
            $value = $2;
            $value =~ s/#/ /g;
            push @{$P{$parameter}},$value;
        }
        else {
            die "Bad format (parameter:value expected): $_";
        }
    }
} #end local scope

# create new many.xml decoder config file by cloning and overriding the original one
open(INI,$infn) or die "Can't read input config: $infn";
open(OUT,"> $outfn") or die "Can't write output config: $outfn";
print "Saving new config to: $outfn\n";
my $line = <INI>;
while(1) {
    last unless $line;
    # skip until hit <property 
    if ($line !~ /^<property name=\"(.+)\"\s+value=\".+\"/) { 
        print OUT $line; # if $line =~ /^\#/ || $line =~ /^\s+$/;
    }
    else {
        # parameter name
        my $parameter = $1;
        
        # change parameter, if new values
        if (defined($P{$parameter})) {
            # write new values
            print OUT '<property name="'.$parameter.'" value="'."@{$P{$parameter}}".'"/>'."\n";
            delete($P{$parameter});
        }
        else # unchanged parameter, write old
        {
            print OUT $line;
        }
    }
    $line = <INI>;
}

close(INI);
close(OUT);
print STDERR "Saved: $outfn\n";	


