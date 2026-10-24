# DRAFT. Usage: fish verify-completions.fish <dir with the golden .fish files>
#
# Loads picocli's generated fish completion scripts into this fish shell and asks fish what it would
# complete (`complete -C`). Only uses features that exist in fish 3.1 and later.

set -g dir $argv[1]
set -g failures 0

source $dir/basic.fish
source $dir/picocompletion-demo_completion.fish
source $dir/fish-features.fish

# expect_has "<command line>" <candidate...>   every candidate must be offered
function expect_has
    set -l line $argv[1]
    set -l offered (complete -C "$line" | string replace -r '\t.*' '')
    for want in $argv[2..-1]
        if not contains -- $want $offered
            echo "FAIL: '$line' should offer '$want', but offered: $offered"
            set -g failures (math $failures + 1)
        end
    end
end

# expect_lacks "<command line>" <candidate...>  none of the candidates may be offered
function expect_lacks
    set -l line $argv[1]
    set -l offered (complete -C "$line" | string replace -r '\t.*' '')
    for unwanted in $argv[2..-1]
        if contains -- $unwanted $offered
            echo "FAIL: '$line' should not offer '$unwanted', but offered: $offered"
            set -g failures (math $failures + 1)
        end
    end
end

# subcommands and options of the root command
expect_has   "picocompletion-demo " sub1 sub2
expect_has   "picocompletion-demo -" --version --help
# once drilled into a subcommand, the parent's options are no longer suggested
expect_has   "picocompletion-demo sub1 --" --num --str --candidates
expect_lacks "picocompletion-demo sub1 --" --version --help
# nested subcommands, scoped by the combined -n condition
expect_has   "picocompletion-demo sub2 " subsub1 subsub2 subsub3
expect_has   "picocompletion-demo sub2 subsub2 -" --timeUnit --timeout
expect_lacks "picocompletion-demo sub2 subsub2 -" --num2 --host
# option values: only the candidates are offered
expect_has   "picocompletion-demo sub1 --candidates " aaa bbb ccc
expect_lacks "picocompletion-demo sub1 --candidates " sub2 --num
expect_has   "picocompletion-demo sub2 --thread-state " NEW RUNNABLE TIMED_WAITING
# a second command, loaded from another file
expect_has   "basicExample --timeUnit " NANOSECONDS DAYS

# negatable options: the negative name is offered too
expect_has   "fishfeatures --" --color --no-color
# inherited options (scope = INHERIT) show up in the subcommand, the parent's own options do not
expect_has   "fishfeatures child --" --own --shared
expect_lacks "fishfeatures child --" --color --pick
# candidates with spaces, quotes and parentheses are each offered as ONE completion, and nothing is executed
rm -f /tmp/picocli-fish-pwned
expect_has   "fishfeatures --pick " plain "with space" "it's" 'a(b)' '$(touch /tmp/picocli-fish-pwned)'
expect_lacks "fishfeatures --pick " with space "it's\\" '$(touch'
if test -e /tmp/picocli-fish-pwned
    echo "FAIL: a completion candidate was executed as a command"
    rm -f /tmp/picocli-fish-pwned
    set -g failures (math $failures + 1)
end
# required value (-r): the value follows, so subcommands are not suggested
expect_lacks "fishfeatures --required " child
# optional value (arity 0..1): the value may be omitted, so subcommands are still suggested ...
expect_has   "fishfeatures --optional " child
expect_has   "fishfeatures --mode " child
# ... and the candidates are offered after --mode=
expect_has   "fishfeatures --mode=" --mode=plain "--mode=with space"
# ... and also as a separate word right after --mode (picocli accepts "--mode plain"), next to the subcommands
expect_has   "fishfeatures --mode " child plain "with space"
expect_has   "fishfeatures --mode plain " child
expect_lacks "fishfeatures --mode plain " plain "with space"
# an optional value without candidates still completes file names after --optional=, although the command has
# subcommands (whose -f line switches off file completion); run in an empty temporary directory with one file
set -g workdir (mktemp -d)
set -g olddir $PWD
cd $workdir
touch picocli-fish-file.txt
expect_has   "fishfeatures --optional=" --optional=picocli-fish-file.txt
expect_has   "fishfeatures --optional " child
cd $olddir
rm -rf $workdir

if test $failures -gt 0
    echo "$failures check(s) failed"
    exit 1
end
echo "all fish completion checks passed"
