import os

output_dir = "src/app/src/main/res/drawable"
os.makedirs(output_dir, exist_ok=True)

# 3x3 Grid Definition
# Viewport 24x24. Each cell is 8x8.
cells = [
    (0,0), (8,0), (16,0),
    (0,8), (8,8), (16,8),
    (0,16), (8,16), (16,16)
]

base_grid_path = "M8,0v24 M16,0v24 M0,8h24 M0,16h24" # Grid lines

for i, (x, y) in enumerate(cells):
    index = i + 1
    filename = f"ic_tap_zone_{index}.xml"
    
    xml_content = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
  <!-- The Grid Lines -->
  <path
      android:strokeColor="#FF000000"
      android:strokeWidth="1"
      android:pathData="{base_grid_path}"/>
      
  <!-- The Highlighted Cell ({index}) -->
  <path
      android:fillColor="#FF000000"
      android:pathData="M{x},{y}h8v8h-8z"/>
</vector>"""
    
    with open(os.path.join(output_dir, filename), "w") as f:
        f.write(xml_content)
    print(f"Generated {filename}")
