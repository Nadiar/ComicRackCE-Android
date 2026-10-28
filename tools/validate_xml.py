import xml.etree.ElementTree as ET
p='app/src/main/res/layout/test_reader_apk.xml'
try:
    tree=ET.parse(p)
    root=tree.getroot()
    print('root tag:', root.tag)
    for child in list(root)[:5]:
        print('child:', child.tag)
except Exception as e:
    print('parse error:', e)