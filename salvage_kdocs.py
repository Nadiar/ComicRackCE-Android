
import os
import subprocess

def run_git(args):
    result = subprocess.run(['git'] + args, capture_output=True, text=True, check=True)
    return result.stdout

def salvage():
    with open('changed_files.txt', 'r') as f:
        files = [line.strip() for line in f if line.strip()]

    for file_path in files:
        if not file_path.endswith('.kt'):
            continue
        
        # Get path relative to git root for git commands, but relative to CWD for file ops
        # (Assuming script is run from git root or adjusted accordingly)
        
        try:
            # Main version (stable)
            main_content = run_git(['show', 'main:' + file_path])
            # Docfix version (rich docs but broken headers)
            doc_content = run_git(['show', 'docfix/pilot-comments:' + file_path])
        except subprocess.CalledProcessError:
            print(f"Skipping {file_path} (could not find in one of the branches)")
            continue

        # Extract package line from main
        package_line = None
        for line in main_content.splitlines():
            if line.startswith('package '):
                package_line = line
                break
        
        if not package_line:
            print(f"Warning: No package line found in main for {file_path}")
            continue

        # Check if doc version has the package line
        has_package = any(line.strip().startswith('package ') for line in doc_content.splitlines())
        
        if not has_package:
            print(f"Repairing {file_path} (inserting package header)")
            # Insert package line at the very top
            # Usually the doc version starts with /** or //
            final_content = package_line + "\n\n" + doc_content
        else:
            final_content = doc_content

        # Write to local file
        # path is relative to repo root, make sure it exists
        abs_path = os.path.join(os.getcwd(), file_path)
        os.makedirs(os.path.dirname(abs_path), exist_ok=True)
        with open(abs_path, 'w', encoding='utf-8') as f:
            f.write(final_content)

if __name__ == '__main__':
    salvage()
