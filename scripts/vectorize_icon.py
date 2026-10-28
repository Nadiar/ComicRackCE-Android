from PIL import Image
from potrace import Bitmap
import argparse

def png_to_svg_potrace(input_png, output_svg):
    """
    Converts a PNG image to an SVG using pypotrace.
    """
    print(f"Processing {input_png}...")
    # Open the PNG image using Pillow
    img = Image.open(input_png)
    
    # Resize to something reasonable for vectorization if too small (optional, but good for 96px icons)
    # img = img.resize((512, 512), Image.Resampling.LANCZOS)

    # Convert to a format suitable for potrace 
    # Example for simple black/white tracing (requires image processing to make binary first)
    # This part might need adjustment based on your image
    # We use a threshold of 128 (middle gray)
    img = img.convert('L').point(lambda x: 0 if x < 128 else 255, '1') # Binarize image
    
    bitmap = Bitmap(img) # Create bitmap from image data
    
    # Trace the bitmap
    path = bitmap.trace()

    # Save the result as an SVG file
    with open(output_svg, "w") as f:
        f.write(path.get_svg_image())
    
    print(f"Successfully converted {input_png} to {output_svg}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    
    png_to_svg_potrace(args.input, args.output)
