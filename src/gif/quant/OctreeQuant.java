package gif.quant;


import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 类说明:
 *
 * @auther: yuanguoyan
 * @version: 19/01/09 09:42
 */
public class OctreeQuant implements Quantizer{
    private Octree _octree;
    public OctreeQuant(){
        this(8);
    }
    public OctreeQuant(int maxColorBits){
        if (maxColorBits < 1 || maxColorBits > 8){
            System.out.println("-----------");
            maxColorBits = 8;
        }
        //Construct the octree.
        _octree = new Octree(maxColorBits);
    }

    @Override
    public byte[] process(byte[] thepic, int sample) {
        int pixCount = thepic.length/3;
        for (int i = 0; i < pixCount; i++) {
            int n = i * 3;
            int blue = thepic[n] & 0xFF;
            int green = thepic[n+1] & 0xFF;
            int red = thepic[n+2] & 0xFF;
            _octree.addColor(new Color(red, green, blue));
        }

        List<Color> palette = getPalette();
        int colors = palette.size();

        byte[] map = new byte[colors*3];
        int k = 0;
        for (int i = 0; i < colors; i++) {
            int rgb  = palette.get(i).getRGB();
            map[k++] = (byte) (rgb >> 0);
            map[k++] = (byte) (rgb >> 8);
            map[k++] = (byte) (rgb >> 16);
        }
        return map;
    }

    int pre_r = -1;
    int pre_g = -1;
    int pre_b = -1;
    int previous = -1;

    @Override
    public int map(int b, int g, int r) {
        if(previous < 0 || r != pre_r || g != pre_g || b != pre_b){
            previous = quantizePixel(new Color(r, g, b));
            pre_r = r;
            pre_g = g;
            pre_b = b;
            return previous;
        }else {
            return previous;
        }
    }


    /**
     * Process the pixel in the first pass of the algorithm
     *
     * This function need only be overridden if your quantize algorithm needs two passes, such as an Octree quantizer.
     *
     * @param pixel
     */
    public void initialQuantizePixel(Color pixel) {
        _octree.addColor(pixel);
    }

    /**
     * Override this to process the pixel in the second pass of the algorithm
     * @param pixel
     * @return
     */
    protected int quantizePixel(Color pixel) {
        return ((byte) _octree.getPaletteIndex(pixel)) & 0x00ff;
    }


    /// <summary>
    /// Retrieve the palette for the quantized image
    /// </summary>
    /// <returns>The new color palette</returns>

    public List<Color> getPalette() {
        //First off convert the octree to _maxColors colors.  - (TransparentColor.HasValue ? 1 : 0)
        List<Color> palette = _octree.palletize(256, Color.black);

        //TODO: Detect if the color was reduced. Not sure how.
//        //Add the transparent color
//        if (TransparentColor.HasValue && !palette.Contains(TransparentColor.Value) && palette.Count == MaxColors) {
//            var closest = ScreenToGif.Util.ColorExtensions.ClosestColorRgb(palette.Cast < Color > ().ToList(), TransparentColor.Value);
//
//            TransparentColor = (Color) palette[closest];
//            //palette.Insert(palette.Count, TransparentColor);
//        }

        //Then convert the palette based on those colors.
        return palette;
    }



    private class Octree {
        /**
         * Mask used when getting the appropriate pixels for a given node
         */
        private int[] Mask = new int[]{0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01};

        /**
         * The root of the octree
         **/
        private OctreeNode _root;


        /** Number of leaves in the tree **/
        private int _leafCount;

        /** Return the array of reducible nodes **/
        protected OctreeNode[] ReducibleNodes;

        /** Maximum number of significant bits in the image **/
        int _maxColorBits;

        /** Store the last node quantized **/
        private OctreeNode _previousNode;

        /** Cache the previous color quantized **/
        private Color _previousColor;


        /**
         * Construct the octree
         * @param maxColorBits : The maximum number of significant bits in the image
         */
        public Octree(int maxColorBits) {
            _maxColorBits = maxColorBits;
            _leafCount = 0;
            ReducibleNodes = new OctreeNode[9];

            _root = new OctreeNode(0, _maxColorBits, this);
            _previousColor = new Color(0,0,0);
            _previousNode = null;
        }

        /**
         * Add a given color value to the octree
         * @param pixel
         */
        public void addColor(Color pixel) {
            //Check if this request is for the same color as the last
            if (_previousColor == pixel) {
                //If so, check if I have a previous node setup. This will only ocurr if the first color in the image
                //happens to be black, with an alpha component of zero.
                if (null == _previousNode) {
                    _previousColor = pixel;
                    _root.addColor(pixel, _maxColorBits, 0, this);
                } else
                    //Just update the previous node
                    _previousNode.increment(pixel);
            } else {
                _previousColor = pixel;
                _root.addColor(pixel, _maxColorBits, 0, this);
            }
        }

        /**
         * Reduce the depth of the tree
         */
        private void reduce() {
            int index;
            //Find the deepest level containing at least one reducible node
            for (index = _maxColorBits - 1; index > 0 && null == ReducibleNodes[index]; index--) ;

            //Reduce the node most recently added to the list at level 'index'
            OctreeNode node = ReducibleNodes[index];
            ReducibleNodes[index] = node.NextReducible;

            //Decrement the leaf count after reducing the node
            _leafCount -= node.reduce();

            //And just in case I've reduced the last color to be added, and the next color to
            //be added is the same, invalidate the previousNode...
            _previousNode = null;
        }

        /**
         * Keep track of the previous node that was quantized
         * @param node : The node last quantized
         */
        protected void trackPrevious(OctreeNode node) {
            _previousNode = node;
        }

        /**
         * Convert the nodes in the octree to a palette with a maximum of colorCount colors.
         * @param colorCount : The maximum number of colors.
         * @param transparent : The transparent color.
         * @return : An list with the palettized colors
         */
        public List<Color> palletize(int colorCount, Color transparent) {
            while (_leafCount > colorCount){
                reduce();
            }
            List<Color> palette = new ArrayList<Color>(_leafCount);
            int[] paletteIndex = {0};
            _root.constructPalette(palette, paletteIndex, transparent);
            return palette;
        }

        /**
         * Get the palette index for the passed color
         * @param pixel
         * @return
         */
        public int getPaletteIndex(Color pixel) {
            try {
                return _root.getPaletteIndex(pixel, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }



        protected class OctreeNode {
            /** Flag indicating that this is a leaf node **/
            private boolean _leaf;

            /** Number of pixels in this node **/
            private int _pixelCount;

            private int _red;
            private int _green;
            private int _blue;

            /** The index of this node in the palette **/
            private int _paletteIndex;

            /**
             * Get/Set the next reducible node.
             */
            public OctreeNode NextReducible;

            /**
             * Pointers to any child nodes.
             */
            public OctreeNode[] Children;

            /**
             * Construct the node
             *
             * @param level:     The level in the tree = 0 - 7
             * @param colorBits: The number of significant color bits in the image
             * @param octree:    The tree to which this node belongs
             */
            public OctreeNode(int level, int colorBits, Octree octree) {
                _leaf = level == colorBits;
                _red = _green = _blue = 0;
                _pixelCount = 0;

                //If a leaf, increment the leaf count.
                if (_leaf) {
                    octree._leafCount ++;
                    NextReducible = null;
                    Children = null;
                } else {
                    //Otherwise add this to the reducible nodes.
                    NextReducible = octree.ReducibleNodes[level];
                    octree.ReducibleNodes[level] = this;
                    Children = new OctreeNode[8];
                }
            }

            /**
             * Add a color into the tree
             * @param pixel : The color
             * @param colorBits : The number of significant color bits
             * @param level: The level in the tree
             * @param octree: The tree to which this node belongs
             */
            public void addColor(Color pixel, int colorBits, int level, Octree octree) {
                // Update the color information if this is a leaf
                if (_leaf) {
                    increment(pixel);
                    octree.trackPrevious(this);
                } else {
                    // Go to the next level down in the tree
                    int shift = 7 - level;
                    int index = ((pixel.getRed() & Mask[level]) >> (shift - 2)) |
                            ((pixel.getGreen() & Mask[level]) >> (shift - 1)) |
                            ((pixel.getBlue() & Mask[level]) >> (shift));

                    OctreeNode child = Children[index];

                    if (null == child) {
                        // Create a new child node & store in the array
                        child = new OctreeNode(level + 1, colorBits, octree);
                        Children[index] = child;
                    }

                    // Add the color to the child node
                    child.addColor(pixel, colorBits, level + 1, octree);
                }
            }




            /**
             * Reduce this node by removing all of its children
             * @return
             */
            public int reduce() {
                _red = _green = _blue = 0;
                int children = 0;
                for (int index = 0; index < 8; index++) {
                    if (null == Children[index]) continue;
                    _red += Children[index]._red;
                    _green += Children[index]._green;
                    _blue += Children[index]._blue;
                    _pixelCount += Children[index]._pixelCount;
                    ++children;
                    Children[index] = null;
                }
                _leaf = true;
                return children - 1;
            }


            /**
             * Traverse the tree, building up the color palette
             * @param palette : The palette
             * @param paletteIndex : The current palette index
             * @param transparent : The transparent color.
             */
            public void constructPalette(List<Color> palette, int[] paletteIndex, Color transparent) {
                if (_leaf) {
                    _paletteIndex = paletteIndex[0]++;
                    byte r = (byte) (_red / _pixelCount);
                    byte g = (byte) (_green / _pixelCount);
                    byte b = (byte) (_blue / _pixelCount);
                    palette.add(new Color(r & 0xFF, g & 0xFF, b & 0xFF));
                } else {
                    //Loop through children looking for leaves.
                    for (int index = 0; index < 8; index++) {
                        if (null != Children[index])
                            Children[index].constructPalette(palette, paletteIndex, transparent);
                    }
                }
            }

            /**
             * Return the palette index for the passed color
             * @param pixel
             * @param level
             * @return
             */
            public int getPaletteIndex(Color pixel, int level) throws Exception {
                int paletteIndex = _paletteIndex;
                if (_leaf) return paletteIndex;

                int shift = 7 - level;
                int index = ((pixel.getRed() & Mask[level]) >> (shift - 2)) |
                        ((pixel.getGreen() & Mask[level]) >> (shift - 1)) |
                        ((pixel.getBlue() & Mask[level]) >> (shift));

                if (null != Children[index]) {
                    paletteIndex = Children[index].getPaletteIndex(pixel, level + 1);
                }
                else {
                    throw new Exception("Not expected!");
                }
//                System.out.println(" index: "+paletteIndex +" color: "+pixel);
                return paletteIndex;
            }

            /**
             * Increment the pixel count and add to the color information
             * @param pixel
             */
            public void increment(Color pixel) {
                _pixelCount++;
                _red += pixel.getRed();
                _green += pixel.getGreen();
                _blue += pixel.getBlue();
            }


        }
    }
}
