// Regenerates BundledSeedJson.kt from the repo files. Usage: node gen-seed.mjs <repoRoot>
import fs from 'node:fs';
import path from 'node:path';
const root = process.argv[2];
const out = path.join(root, 'native-app/app/src/commonMain/kotlin/app/masroufy/seed/BundledSeedJson.kt');
const tree = JSON.parse(fs.readFileSync(path.join(root, 'src/infrastructure/import/categoryTree.json'), 'utf8'));
const rules = JSON.parse(fs.readFileSync(path.join(root, 'design-source/masroofi-claude-code/fixtures/rule-reference.json'), 'utf8'));
const old = fs.readFileSync(out, 'utf8');
const eol = old.includes('\r\n') ? '\r\n' : '\n';
const header = old.split(/\r?\n/).slice(0, 6);
const t = JSON.stringify(tree);
const r = rules.map((x) => JSON.stringify(x)).join(',' + eol);
if (t.includes('$') || r.includes('$') || t.includes('"""') || r.includes('"""')) throw new Error('raw-string unsafe');
const body = [...header, `internal val CATEGORY_TREE_JSON: String = """${t}"""`, '', `internal val RULE_REFERENCE_JSON: String = """[`, r, ']"""', ''].join(eol);
fs.writeFileSync(out, body);
console.log('written', out);
