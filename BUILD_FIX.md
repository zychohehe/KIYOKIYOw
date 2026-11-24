# Build fix: duplicate string resources

The release build failed with about 37,000 "Duplicate resources" errors. Each
`values-*/` folder had both `kiyo_strings.xml` and `fizy_strings.xml`, with the
same strings. The newer `fizy_strings.xml` replaces the old file, so the old one
has to go.

Run from the repository root, then commit and push:

    find app/src/main/res -name "kiyo_strings.xml" -print -delete
    git add -A
    git commit -m "Remove leftover kiyo_strings.xml (duplicate resources)"
    git push

Check that the build passes:

    ./gradlew :app:mergeReleaseResources

No `kiyo_strings.xml` file remains in this zip. Keep `fizy_strings.xml`.
