#!/usr/bin/env python3
"""
batch_deobfuscate.py
Automates the renaming of obfuscated classes across the project.
Optimized for single-pass global updates.
"""

import os
import re
import json
import argparse
from pathlib import Path

def parse_mappings(mapping_file):
    """Parses RENAMING_SUGGESTIONS for package-grouped class mappings (supports .md and .jsonl)."""
    mappings = []
    
    if mapping_file.endswith('.jsonl'):
        with open(mapping_file, 'r', encoding='utf-8') as f:
            for line in f:
                if not line.strip(): continue
                item = json.loads(line)
                orig_fqn = item['original_fqn']
                sugg_fqn = item['suggested_fqn']
                
                # Resolve package and local names from FQN
                # Handle cases with method signatures like .a(Bundle)
                base_orig = orig_fqn.split('(')[0]
                pkg_parts = base_orig.split('.')
                
                if item['type'] in ['class', 'interface', 'enum']:
                    pkg = '.'.join(pkg_parts[:-1])
                    orig_name = pkg_parts[-1]
                    sugg_name = sugg_fqn.split('.')[-1]
                else:
                    # Method or Variable: com.pkg.Class.name
                    pkg = '.'.join(pkg_parts[:-2])
                    # We store the parent class info in the 'package' field temporarily 
                    # or adjust how the script handles them.
                    # For now, let's keep them in the list; the script filters for Class-like types later.
                    orig_name = pkg_parts[-1]
                    sugg_name = sugg_fqn.split('.')[-1]

                mappings.append({
                    "package": pkg,
                    "original": orig_name,
                    "suggested": sugg_name,
                    "kind": item['type'],
                    "fqn": orig_fqn,
                    "new_fqn": sugg_fqn
                })
        return mappings

    current_package = ""
    
    package_re = re.compile(r'## `(.+)` Package')
    mapping_re = re.compile(r'\| `([^`]+)` \| `([^`]+)` \| ([^|]+) \|')
    
    with open(mapping_file, 'r', encoding='utf-8') as f:
        for line in f:
            pkg_match = package_re.search(line)
            if pkg_match:
                current_package = pkg_match.group(1)
                continue
                
            map_match = mapping_re.search(line)
            if map_match:
                original = map_match.group(1).strip()
                suggested = map_match.group(2).strip()
                kind = map_match.group(3).strip()
                
                if original == "Original Name": continue
                
                # CRITICAL: Only include Class-like types to avoid ruining variables
                if kind.lower() not in ['class', 'interface', 'enum']:
                    continue
                
                # Handle FQN in the original name
                if '.' in original:
                    pkg = '.'.join(original.split('.')[:-1])
                    orig_name = original.split('.')[-1]
                    mappings.append({
                        "package": pkg,
                        "original": orig_name,
                        "suggested": suggested,
                        "kind": kind,
                        "fqn": original,
                        "new_fqn": f"{pkg}.{suggested}"
                    })
                elif current_package:
                    mappings.append({
                        "package": current_package,
                        "original": original,
                        "suggested": suggested,
                        "kind": kind,
                        "fqn": f"{current_package}.{original}",
                        "new_fqn": f"{current_package}.{suggested}"
                    })
                else:
                    print(f"Warning: Skipping mapping '{original}' - no package context found.")
    return mappings

def resolve_paths(mappings, tags_file):
    """Uses tags.json to find the actual file path for each class declaration."""
    if not os.path.exists(tags_file):
        print(f"Error: {tags_file} not found. Run update-ctags.ps1 first.")
        return []
        
    try:
        with open(tags_file, 'r', encoding='utf-8') as f:
            tags = json.load(f)
    except (json.JSONDecodeError, ValueError):
        with open(tags_file, 'r', encoding='utf-8') as f:
            tags = [json.loads(line) for line in f if line.strip()]

    class_tags = [t for t in tags if t.get('kind') in ['class', 'interface', 'enum']]
    
    resolved = []
    for m in mappings:
        # Resolve by both name and package-derived path
        expected_suffix = os.path.join(*m['package'].split('.'), f"{m['original']}.java")
        expected_suffix = expected_suffix.replace('\\', '/')
        
        found = False
        for tag in class_tags:
            tag_path = tag['path'].replace('\\', '/')
            if tag_path.endswith(expected_suffix):
                m['path'] = tag['path']
                resolved.append(m)
                found = True
                break
        if not found:
            # Check if it was already renamed (descripve name exists instead)
            desc_suffix = os.path.join(*m['package'].split('.'), f"{m['suggested']}.java")
            desc_suffix = desc_suffix.replace('\\', '/')
            for tag in class_tags:
                tag_path = tag['path'].replace('\\', '/')
                if tag_path.endswith(desc_suffix):
                    print(f"Skipping {m['fqn']}: Already seems renamed to {m['suggested']}")
                    found = True
                    break
        
        if not found:
            print(f"Warning: Could not resolve source for {m['fqn']}")
                
    return resolved

def build_replacement_map(mappings):
    """Builds a map for fast lookup during the global pass."""
    fqn_map = {}
    name_map = {}
    alias_paths = set()
    for m in mappings:
        fqn_map[m['fqn']] = m['new_fqn']
        if m['package'] not in name_map:
            name_map[m['package']] = {}
        name_map[m['package']][m['original']] = m['suggested']
        if 'path' in m:
            alias_paths.add(Path(m['path']).name) # e.g. "ag.java"
    return fqn_map, name_map, alias_paths

def apply_global_updates(src_root, fqn_map, name_map, alias_filenames, dry_run=False):
    """Performs a single pass over all Java files to update references."""
    print("Performing global reference update pass...")
    
    if not fqn_map: return
    
    # CRITICAL SAFETY: Filter out mappings where original == suggested (case-insensitive)
    safe_fqn_map = {}
    for k, v in fqn_map.items():
        if k.lower() != v.lower():
            safe_fqn_map[k] = v
        else:
            print(f"  SAFETY: Skipping self-mapping: {k}")
    
    fqn_pattern = re.compile(r'|'.join([re.escape(k) for k in safe_fqn_map.keys()]))
    
    count = 0
    for java_file in Path(src_root).rglob("*.java"):
        # CRITICAL: Skip the Alias files themselves, otherwise we get "class X extends X"
        if java_file.name in alias_filenames:
            # Check if it's the right package before skipping? 
            # For simplicity, if it's "ag.java", we assume it's our alias.
            continue
            
        with open(java_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        new_content = content
        
        # 1. Update FQNs and Imports
        def fqn_replacer(match):
            return safe_fqn_map[match.group(0)]
        new_content = fqn_pattern.sub(fqn_replacer, new_content)
        
        # 2. Update simple names
        pkg_match = re.search(r'package\s+([\w\.]+);', new_content)
        current_pkg = pkg_match.group(1) if pkg_match else ""
        
        in_scope_names = {}
        if current_pkg in name_map:
            # CRITICAL: Only add names where original != suggested
            for orig, sugg in name_map[current_pkg].items():
                if orig.lower() != sugg.lower():
                    in_scope_names[orig] = sugg
            
        for pkg, names in name_map.items():
            for orig, sugg in names.items():
                if orig.lower() == sugg.lower():
                    continue  # Skip self-mappings
                if f"import {pkg}.{orig};" in new_content or f"import {pkg}.{sugg};" in new_content or f"import {pkg}.*;" in new_content:
                    in_scope_names[orig] = sugg
        
        if in_scope_names:
            # CRITICAL SAFETY: 
            # 1. Split names into "Safe" ( > 2 chars) and "Risky" ( <= 2 chars)
            safe_names = {k: v for k, v in in_scope_names.items() if len(k) > 2}
            risky_names = {k: v for k, v in in_scope_names.items() if len(k) <= 2}
            
            # For safe names, we use the standard word boundary check
            if safe_names:
                name_pattern = re.compile(rf'(?<!\.)\b({"|".join([re.escape(n) for n in safe_names.keys()])})\b')
                def name_replacer(match):
                    return safe_names[match.group(1)]
                new_content = name_pattern.sub(name_replacer, new_content)
            
            # For risky names, we ONLY replace if they look like Type usages
            # (e.g. preceded by new, instanceof, (, <, or followed by .class, .static, etc.)
            # This is still not perfect but much safer than global word boundary.
            if risky_names:
                # We'll use a more restrictive pattern for risky names
                for orig, sugg in risky_names.items():
                    # 1. Usages with boundary markers (., [, <)
                    # Note: We EXCLUDE '(' because it's too likely to be a method for single letters
                    risky_pattern = re.compile(rf'(?<!\.)\b{re.escape(orig)}\b(?=[\.\[\<])')
                    new_content = risky_pattern.sub(sugg, new_content)
                    
                    # 2. Usages after keywords (new, instanceof)
                    # For 'new', we DO allow '(' because it's a constructor
                    new_pattern = re.compile(rf'\b(new)\s+{re.escape(orig)}\b(?=[\s\(\[\<])')
                    new_content = new_pattern.sub(rf'\1 {sugg}', new_content)
                    
                    instanceof_pattern = re.compile(rf'\b(instanceof)\s+{re.escape(orig)}\b')
                    new_content = instanceof_pattern.sub(rf'\1 {sugg}', new_content)

                    # 3. Casts: (Class)
                    cast_pattern = re.compile(rf'\(\s*{re.escape(orig)}\s*\)')
                    new_content = cast_pattern.sub(f'({sugg})', new_content)

        if new_content != content:
            count += 1
            if not dry_run:
                with open(java_file, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                    
    print(f"Updated references in {count} files.")

def main():
    parser = argparse.ArgumentParser(description="Batch de-obfuscate Java classes.")
    parser.add_argument("--mappings", default=".copilot/RENAMING_SUGGESTIONS.jsonl")
    parser.add_argument("--tags", default=".github/ctags/tags.json")
    parser.add_argument("--map", nargs='+', help="Manual mappings in 'Original:Suggested:Package:Kind' format")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    # 1. Load Mappings (from file and/or manual)
    mappings = []
    if args.mappings and os.path.exists(args.mappings):
        mappings = parse_mappings(args.mappings)
        print(f"Loaded {len(mappings)} mappings from {args.mappings}")

    if args.map:
        for m_str in args.map:
            try:
                orig, sugg, pkg, kind = m_str.split(':')
                # CRITICAL VALIDATION: Reject identical mappings
                if orig.lower() == sugg.lower():
                    print(f"ERROR: Skipping mapping with identical original and suggested names (case-insensitive): {pkg}.{orig} -> {sugg}")
                    continue
                mappings.append({
                    "package": pkg,
                    "original": orig,
                    "suggested": sugg,
                    "kind": kind,
                    "fqn": f"{pkg}.{orig}",
                    "new_fqn": f"{pkg}.{sugg}"
                })
                print(f"Added manual mapping: {pkg}.{orig} -> {sugg}")
            except ValueError:
                print(f"Warning: Invalid manual mapping format '{m_str}'. Use 'Original:Suggested:Package:Kind'")

    # 2. Resolve Paths
    resolved = resolve_paths(mappings, args.tags)
    
    if args.dry_run:
        print("[DRY RUN] No changes will be made.")

    # 2. Rename Files and update declarations
    for m in resolved:
        original_path = Path(m['path'])
        new_path = original_path.parent / f"{m['suggested']}.java"
        
        print(f"Declaration: {m['fqn']} -> {m['suggested']}")
        
        # CRITICAL SAFETY CHECK: Avoid creating "class X extends X" scenarios
        if m['original'].lower() == m['suggested'].lower():
            print(f"  SKIPPING: Original and suggested names are identical (case-insensitive): {m['original']}")
            continue
        
        # SAFETY: Check if original file exists
        if not original_path.exists():
            print(f"  SKIPPING: Original file does not exist (may have been deleted): {original_path}")
            continue
        
        if not args.dry_run:
            with open(original_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # CRITICAL: Check if new_path already exists (would cause circular reference)
            if new_path.exists():
                print(f"  SKIPPING: Target file {new_path.name} already exists. This would create a circular reference.")
                continue
            
            # Update class/interface/enum declaration
            # CRITICAL: We strip 'final' because the Alias must extend this class.
            content = re.sub(rf'\bfinal\s+(class|interface|enum)\s+{re.escape(m["original"])}\b', f'\\1 {m["suggested"]}', content)
            content = re.sub(rf'\b(class|interface|enum)\s+{re.escape(m["original"])}\b', f'\\1 {m["suggested"]}', content)
            # Update constructor
            content = re.sub(rf'\b{re.escape(m["original"])}\b(?=\s*\()', m['suggested'], content)
            
            # Write renamed file with updated declaration
            with open(new_path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"  Renamed: {original_path.name} -> {new_path.name}")
            
            # Create Deprecated Alias (the original file path now becomes the alias)
            # CRITICAL: Enums are final and cannot be extended, so skip alias for enums
            if m['kind'].lower() == "enum":
                # For enums, we cannot create aliases, so delete the original file
                original_path.unlink()
                print(f"  Deleted original enum: {original_path.name} (enums cannot have extending aliases)")
            else:
                # CRITICAL: The alias MUST reference a different class name (the new_path's class)
                alias_type = "interface" if m['kind'].lower() == "interface" else "class"
                with open(original_path, 'w', encoding='utf-8') as f:
                    f.write(f"package {m['package']};\n\n")
                    f.write(f"/** @deprecated Use {{ @link {m['suggested']} }} instead. */\n")
                    f.write(f"@Deprecated\n")
                    f.write(f"public {alias_type} {m['original']} extends {m['suggested']} {{\n")
                    if alias_type == "class":
                        f.write(f"    // Legacy alias for backward compatibility\n")
                    f.write(f"}}\n")
                print(f"  Created alias: {original_path.name} (extends {m['suggested']})")

    # 3. Global reference update (Single Pass)
    fqn_map, name_map, alias_filenames = build_replacement_map(resolved)
    apply_global_updates("src", fqn_map, name_map, alias_filenames, args.dry_run)

if __name__ == "__main__":
    main()
