#!/usr/bin/env bash
# Usage: ./demo.sh prove | fix [CVE-YYYY-NNNNN] | reset
set -euo pipefail

cd "$(dirname "$0")"

MVN_FLAGS=(-B -q -Dkotlin.compiler.daemon=false)
PLUGIN=org.openrewrite.maven:rewrite-maven-plugin:6.46.1

prove() {
    (cd vulnerable-app && mvn "${MVN_FLAGS[@]}" clean test -Dmaven.test.failure.ignore=true) >/tmp/demo-prove.log 2>&1 || {
        tail -20 /tmp/demo-prove.log
        exit 1
    }
    local boot
    boot=$(sed -n 's:.*<version>\(4\.[0-9.]*\)</version>.*:\1:p' vulnerable-app/pom.xml | head -1)
    echo "Spring Boot $boot"
    for report in vulnerable-app/target/surefire-reports/TEST-*Cve*.xml; do
        local name verdict
        name=$(basename "$report" | sed -E 's/.*Cve([0-9]{4})([0-9]+).*/CVE-\1-\2/')
        if grep -q "VULNERABLE to" "$report"; then
            verdict="VULNERABLE"
        elif grep -qE '<(failure|error)[ >]' "$report"; then
            verdict="TEST BROKEN, see the report"
        else
            verdict="not vulnerable"
        fi
        printf '  %-16s %s\n' "$name" "$verdict"
    done
}

fix() {
    # Without an argument, the glob applies every recipe in ascending CVE order.
    local recipes=(vulnerable-app/recipes/*.yml)
    [[ -n "${1:-}" ]] && recipes=("vulnerable-app/recipes/$1.yml")
    for recipe in "${recipes[@]}"; do
        [[ -f "$recipe" ]] || { echo "No recipe at $recipe" >&2; exit 2; }
        local cve name
        cve=$(basename "$recipe" .yml)
        name=$(sed -n 's/^name: //p' "$recipe")
        echo "Applying $cve"
        (cd vulnerable-app && mvn -B -Dkotlin.compiler.daemon=false "$PLUGIN:run" \
            -Drewrite.configLocation="recipes/$cve.yml" \
            -Drewrite.activeRecipes="$name") >/tmp/demo-fix.log 2>&1 || {
            tail -20 /tmp/demo-fix.log
            exit 1
        }
        grep -E 'Changes have been made' /tmp/demo-fix.log || echo "  no changes needed"
    done
    git --no-pager diff -- vulnerable-app
}

reset() {
    git checkout -- vulnerable-app
    git clean -fdq -- vulnerable-app
}

case "${1:-}" in
    prove) prove ;;
    fix) fix "${2:-}" ;;
    reset) reset ;;
    *) echo "Usage: $0 prove | fix [CVE-YYYY-NNNNN] | reset" >&2; exit 2 ;;
esac
