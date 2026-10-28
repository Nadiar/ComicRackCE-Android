import json
import os
import re

TAGS_FILE = r".github\ctags\tags.json"
MAPPINGS_FILE = r".copilot\RENAMING_SUGGESTIONS.jsonl"
JAVA_BASE = r"src\app\src\main\java"

def get_fqn_from_path(path):
    # Normalize path and find the Java base
    path = path.replace('\\', '/')
    match = re.search(r'src/app/src/main/java/(.+)\.java$', path)
    if match:
        return match.group(1).replace('/', '.')
    return None

def analyze():
    # 1. Load captured FQNs
    captured_fqns = set()
    if os.path.exists(MAPPINGS_FILE):
        with open(MAPPINGS_FILE, 'r', encoding='utf-8') as f:
            for line in f:
                try:
                    data = json.loads(line)
                    captured_fqns.add(data['original_fqn'])
                except:
                    pass
    
    # 2. Parse CTags
    uncaptured = []
    
    if not os.path.exists(TAGS_FILE):
        print(f"Error: {TAGS_FILE} not found.")
        return

    with open(TAGS_FILE, 'r', encoding='utf-8') as f:
        for line in f:
            if not line.strip(): continue
            try:
                tag = json.loads(line)
                if tag.get('_type') != 'tag': continue
                
                path = tag.get('path', '')
                if '.java' not in path or JAVA_BASE.lower() not in path.lower().replace('/', '\\'):
                    continue
                
                name = tag.get('name', '')
                kind = tag.get('kind', '')
                
                # Reconstruct FQN
                base_fqn = get_fqn_from_path(path)
                if not base_fqn: continue
                
                fqn = ""
                if kind in ['class', 'interface', 'enum']:
                    fqn = base_fqn
                elif kind in ['method', 'field']:
                    # Simple heuristic: class_fqn.member_name
                    # Note: this doesn't handle inner classes perfectly but is good for top-level obfuscation
                    fqn = f"{base_fqn}.{name}"
                    # Add paren for methods if needed (though mapping uses signatures sometimes)
                    # Let's check both with and without signature in our captured set
                    # Mapping seems to include signature sometimes, e.g. "ak.c(int)"
                    # CTags signature field:
                    signature = tag.get('signature', '')
                    if kind == 'method' and signature:
                        # Clean signature (remove space after comma)
                        clean_sig = signature.replace(', ', ',')
                        fqn_with_sig = f"{fqn}{clean_sig}"
                        if fqn_with_sig in captured_fqns:
                            continue
                
                if fqn and fqn not in captured_fqns:
                    # Filter for obfuscated
                    if len(name) <= 2:
                        uncaptured.append({
                            'fqn': fqn,
                            'name': name,
                            'kind': kind,
                            'path': path
                        })
            except Exception as e:
                # print(f"Error parsing line: {e}")
                pass

    # 3. Report
    print(f"Total uncaptured obfuscated symbols found: {len(uncaptured)}")
    
    # Group by package/class
    by_file = {}
    for item in uncaptured:
        base = '.'.join(item['fqn'].split('.')[:-1]) if item['kind'] != 'class' else item['fqn']
        if base not in by_file: by_file[base] = []
        by_file[base].append(item)
        
    # Sort files by number of uncaptured symbols
    sorted_files = sorted(by_file.items(), key=lambda x: len(x[1]), reverse=True)
    
    print("\nTop areas with high obfuscation density (uncaptured):")
    for file_fqn, items in sorted_files[:15]:
        classes = sum(1 for i in items if i['kind'] in ['class', 'interface', 'enum'])
        members = len(items) - classes
        print(f"- {file_fqn}: {len(items)} symbols ({classes} class, {members} members)")
        # Show a few examples
        examples = ", ".join([i['name'] for i in items[:10]])
        if len(items) > 10: examples += "..."
        print(f"  Examples: {examples}")

if __name__ == "__main__":
    analyze()
