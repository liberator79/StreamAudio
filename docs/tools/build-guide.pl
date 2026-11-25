#!/usr/bin/env perl
# Builds docs/how-streamaudio-works.html from docs/tools/guide.template.html + the real source files.
#
# Usage (from the repository root):   perl docs/tools/build-guide.pl
#
# Template directives (each on its own line):
#   @@FILE <path from repo root> | <title>@@   start walking through a file
#   @@AT <literal text>@@                      a block starts at the next line containing this text;
#                                              the HTML after the directive explains that block
#   @@ENDFILE@@                                finish the file (the last block runs to end of file)
# Lines before the first @@AT@@ (package + imports) are shown as a collapsed block.
# Every line of every file is printed, with its real line number. A missing anchor is a hard
# error, so the guide can never silently drift out of sync with the code.
use strict;
use warnings;

my $root = '.';
my $template = "$root/docs/tools/guide.template.html";
my $output = "$root/docs/how-streamaudio-works.html";

open my $tf, '<:encoding(UTF-8)', $template or die "Can't read $template: $!";
my @tpl = <$tf>;
close $tf;

my @kw = qw(package import class interface object fun val var if else when for while do return
    try catch finally throw in is as private internal public protected override open abstract
    sealed data enum companion inner const lateinit by get set init this super null true false
    actual expect suspend inline operator break continue typealias vararg);
my %kw = map { $_ => 1 } @kw;

sub esc { my $s = shift; $s =~ s/&/&amp;/g; $s =~ s/</&lt;/g; $s =~ s/>/&gt;/g; return $s; }

# Minimal Kotlin/XML highlighter. Tracks /* */ comments across lines.
my $in_block_comment = 0;
sub highlight {
    my ($line, $is_xml) = @_;
    if ($is_xml) {
        my $h = esc($line);
        $h =~ s{(&lt;!--.*?--&gt;)}{<span class="c">$1</span>}g;
        $h =~ s{(&quot;|")([^"]*)(")}{<span class="s">$1$2$3</span>}g;
        return $h;
    }
    my $out = '';
    while (length $line) {
        if ($in_block_comment) {
            if ($line =~ s{^(.*?\*/)}{}) { $out .= '<span class="c">' . esc($1) . '</span>'; $in_block_comment = 0; }
            else { $out .= '<span class="c">' . esc($line) . '</span>'; $line = ''; }
            next;
        }
        if ($line =~ s{^(/\*)}{}) { $in_block_comment = 1; $out .= '<span class="c">' . esc($1) . '</span>'; next; }
        if ($line =~ s{^(//.*)}{}) { $out .= '<span class="c">' . esc($1) . '</span>'; next; }
        if ($line =~ s{^("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])')}{}) { $out .= '<span class="s">' . esc($1) . '</span>'; next; }
        if ($line =~ s{^(@[A-Za-z_][\w.]*)}{}) { $out .= '<span class="a">' . esc($1) . '</span>'; next; }
        if ($line =~ s{^(0x[0-9A-Fa-f_]+L?|\d[\d_]*(?:\.\d+)?[fFL]?)}{}) { $out .= '<span class="n">' . esc($1) . '</span>'; next; }
        if ($line =~ s{^([A-Za-z_]\w*)}{}) {
            my $w = $1;
            $out .= $kw{$w} ? "<span class=\"k\">$w</span>" : ($w =~ /^[A-Z]/ ? "<span class=\"t\">$w</span>" : $w);
            next;
        }
        $line =~ s{^(.)}{}s; $out .= esc($1);
    }
    return $out;
}

sub code_block {
    my ($lines, $from, $to, $is_xml) = @_;   # 1-based inclusive
    my $html = '';
    for my $n ($from .. $to) {
        my $text = $lines->[$n - 1];
        $text =~ s/\r?\n$//;
        $html .= sprintf(qq{<span class="ln" data-n="%d"></span>%s\n}, $n, highlight($text, $is_xml));
    }
    return qq{<pre class="code"><code>$html</code></pre>\n};
}

my @out;
my $toc = '';
my $i = 0;
while ($i < @tpl) {
    my $t = $tpl[$i];
    if ($t =~ /^\s*\@\@FILE\s+(.+?)\s*\|\s*(.+?)\s*\@\@\s*$/) {
        my ($path, $title) = ($1, $2);
        open my $sf, '<:encoding(UTF-8)', "$root/$path" or die "Can't read $path: $!";
        my @src = <$sf>;
        close $sf;
        my $is_xml = $path =~ /\.xml$/;
        $in_block_comment = 0;
        (my $id = $path) =~ s/[^A-Za-z0-9]+/-/g;
        $toc .= qq{<li><a href="#$id">$title</a></li>\n};
        push @out, qq{<section class="file" id="$id"><h3>$title</h3><p class="path"><code>$path</code> &middot; } .
            scalar(@src) . qq{ lines</p>\n};

        # Collect this file's blocks: [anchor, explanation-html]
        my @blocks;
        $i++;
        while ($i < @tpl && $tpl[$i] !~ /^\s*\@\@ENDFILE\@\@/) {
            if ($tpl[$i] =~ /^\s*\@\@AT\s+(.+?)\@\@\s*$/) {
                push @blocks, [$1, ''];
            } else {
                die "Text before first \@\@AT\@\@ in $path (template line " . ($i + 1) . ")\n" unless @blocks || $tpl[$i] =~ /^\s*$/;
                $blocks[-1][1] .= $tpl[$i] if @blocks;
            }
            $i++;
        }
        die "Missing \@\@ENDFILE\@\@ for $path\n" if $i >= @tpl;
        $i++;

        # Resolve anchors to line numbers, in order.
        my $cursor = 1;
        for my $b (@blocks) {
            my $found;
            for my $n ($cursor .. scalar(@src)) {
                if (index($src[$n - 1], $b->[0]) >= 0) { $found = $n; last; }
            }
            die "Anchor not found in $path after line $cursor: '$b->[0]'\n" unless $found;
            # Pull the doc comment / annotations directly above the anchor into its block.
            while ($found - 1 >= $cursor && $src[$found - 2] =~ m{^\s*(\@|/\*\*|\*|\*/)}) { $found--; }
            $b->[2] = $found;
            $cursor = $found + 1;
        }
        if (@blocks && $blocks[0][2] > 1) {
            my $end = $blocks[0][2] - 1;
            push @out, qq{<details class="imports"><summary>Lines 1&ndash;$end: package declaration and imports</summary>\n} .
                code_block(\@src, 1, $end, $is_xml) . qq{</details>\n};
        }
        for my $k (0 .. $#blocks) {
            my $from = $blocks[$k][2];
            my $to = $k < $#blocks ? $blocks[$k + 1][2] - 1 : scalar(@src);
            # Trim trailing blank lines into the block anyway (keeps every line visible).
            push @out, qq{<div class="block"><div class="range">Lines $from&ndash;$to</div>\n} .
                code_block(\@src, $from, $to, $is_xml) .
                qq{<div class="explain">$blocks[$k][1]</div></div>\n};
        }
        push @out, "</section>\n";
        next;
    }
    push @out, $t;
    $i++;
}

my $html = join '', @out;
$html =~ s/\@\@TOC\@\@/$toc/;
open my $of, '>:encoding(UTF-8)', $output or die "Can't write $output: $!";
print $of $html;
close $of;
print "Wrote $output\n";
