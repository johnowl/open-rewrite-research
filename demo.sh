#!/usr/bin/env bash
# Usage: ./demo.sh prove | fix | reset
set -euo pipefail

cd "$(dirname "$0")"

MVN_FLAGS=(-B -q -Dkotlin.compiler.daemon=false)
PLUGIN=org.openrewrite.maven:rewrite-maven-plugin:6.46.1
RECIPE_JARS=com.example:rewrite-recipes:0.1.0-SNAPSHOT,org.openrewrite.recipe:rewrite-spring:6.37.1

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
    (cd rewrite-recipes && mvn "${MVN_FLAGS[@]}" install -DskipTests)
    (cd vulnerable-app && mvn -B -Dkotlin.compiler.daemon=false "$PLUGIN:run" \
        -Drewrite.activeRecipes=com.example.tickets.FixVulnerabilities \
        -Drewrite.recipeArtifactCoordinates="$RECIPE_JARS") | grep -E 'Changes have been made|Generated new file|^\[WARNING\] +[a-z]+\.|BUILD' || true
    git --no-pager diff --stat -- vulnerable-app
}

reset() {
    git checkout -- vulnerable-app
    git clean -fdq -- vulnerable-app
}

case "${1:-}" in
    prove) prove ;;
    fix) fix ;;
    reset) reset ;;
    *) echo "Usage: $0 prove | fix | reset" >&2; exit 2 ;;
esac
