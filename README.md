# images

Taixue maintenance source for Paper 26.3 / Java 25.

## Build

Install JDK 25, Python 3 and GitHub CLI. Authenticate `gh` with read access to taixue-cn/minecraft-plugin-build-sdk, then run:

```sh
python3 build.py
```

`build-config.json` lists the exact adapted sources; `source/` retains the available upstream or recovered source. `vendor/original.jar` is a hash-locked build input preserving unchanged classes, bundled dependencies and resources. This is an overlay build, not a claim that every bundled dependency was reconstructed from source. To change another class, add its source to the compile list. Source provenance and original hash are recorded in SOURCE-PROVENANCE.json. Upstream license notices are retained in source and JARs.

The SDK release and every dependency are checked against sdk-lock.json before compilation. Output is under `dist/`, with SHA256SUMS. Actions builds on main and PRs; successful main builds publish a release tied to the exact commit. Configure SOURCE_READ_TOKEN with read access to the private SDK repository.

## Server updates

Back up the installed JAR, place the replacement in plugins/update/, and wait for an explicitly authorized restart. A published distribution does not mean it is loaded on a server. No workflow deploys, stops, restarts or reloads Minecraft.
