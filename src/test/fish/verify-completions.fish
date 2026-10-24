# DRAFT. Usage: fish verify-completions.fish <dir with the golden .fish files>
#
# Loads picocli's generated fish completion scripts into this fish shell and asks fish what it would
# complete (`complete -C`). Only uses features that exist in fish 3.1 and later.

set -g dir $argv[1]
set -g failures 0

source $dir/basic.fish
source $dir/picocompletion-demo_completion.fish

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

if test $failures -gt 0
    echo "$failures check(s) failed"
    exit 1
end
echo "all fish completion checks passed"
