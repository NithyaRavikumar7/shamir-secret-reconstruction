# shamir-secret-reconstruction
Solution for Shamir Secret Sharing assignment

# Hashira Secret Reconstruction (Shamir-style)

## Build
Requires Java 17+. Add `org.json` (JSON-Java) library:
- Maven:
  <dependency>
    <groupId>org.json</groupId>
    <artifactId>json</artifactId>
    <version>20231013</version>
  </dependency>

- Or download the jar (json-20231013.jar) and compile with:
  javac -cp json-20231013.jar Main.java

## Run
java -cp .:json-20231013.jar Main input.json

## What it does
- Parses `n`, `k` and shares `{ "x": { "base": "...", "value": "..." } }`.
- `value` can be a literal in its base (2..36) or an expression: ADD/SUB/MUL/DIV with nested args, all in that base.
- Computes the secret (constant term) using exact rational Lagrange interpolation.
- Handles corruption by majority vote over all K-subsets and flags wrong shares.

## Output
Secret: <integer>
Wrong shares: [x=<x>: expected=<y_expected>, got=<y_provided>, ...]

## Complexity
- Combos: C(n, k). With n<=10 and k<=7 (as in test), worst C(10,7)=120.
- Interpolation per subset: O(k^2).
- All big-int / exact rational arithmetic (no floating point).
