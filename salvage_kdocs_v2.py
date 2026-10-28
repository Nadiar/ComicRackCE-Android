
import os
import subprocess

def run_git(args):
    try:
        result = subprocess.run(['git'] + args, capture_output=True, text=True, check=True)
        return result.stdout
    except subprocess.CalledProcessError as e:
        print(f"Git error running {' '.join(args)}: {e.stderr}")
        return None

def salvage():
    # Only process Kotlin files
    files_raw = run_git(['diff', 'main', 'docfix/pilot-comments', '--name-only', '--', '*.kt'])
    if not files_raw:
        print("No changed Kotlin files found.")
        return
        
    files = [line.strip() for line in files_raw.splitlines() if line.strip()]
    print(f"Found {len(files)} Kotlin files to process.")

    for file_path in files:
        # Main version (stable)
        main_content = run_git(['show', 'main:' + file_path])
        # Docfix version (rich docs but broken headers)
        doc_content = run_git(['show', 'docfix/pilot-comments:' + file_path])
        
        if main_content is None or doc_content is None:
            print(f"Skipping {file_path} due to git error.")
            continue

        # Extract package line from main
        package_line = None
        main_lines = main_content.splitlines()
        for line in main_lines:
            if line.strip().startswith('package '):
                package_line = line
                break
        
        if not package_line:
            # Maybe it's a file without a package line? (rare in this project)
            print(f"Info: No package line found in main for {file_path}")
            # Still apply doc_content but don't prepend
            final_content = doc_content
        else:
            # Check if doc version has the package line
            has_package = any(line.strip().startswith('package ') for line in doc_content.splitlines())
            
            if not has_package:
                print(f"Repairing {file_path} (inserting package header)")
                # Insert package line at the very top
                # (Removing any leading empty lines from doc_content first)
                doc_lines = doc_content.splitlines()
                while doc_lines and not doc_lines[0].strip():
                    doc_lines.pop(0)
                final_content = package_line + "\n\n" + "\n".join(doc_lines)
            else:
                # Package line already exists, just use doc version
                final_content = doc_content

        # Write to local file
        # path is relative to repo root
        abs_path = os.path.join(os.getcwd(), file_path)
        os.makedirs(os.path.dirname(abs_path), exist_ok=True)
        with open(abs_path, 'w', encoding='utf-8') as f:
            f.write(final_content)

if __name__ == '__main__':
    salvage()
