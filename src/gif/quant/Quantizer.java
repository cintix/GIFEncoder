package gif.quant;

/**
 * 类说明:
 *
 * @auther: yuanguoyan
 * @version: 19/01/08 13:54
 */
public interface Quantizer {

    byte[] process(byte[] thepic, int sample);

    /**
     * Search for BGR values 0..255 (after net is unbiased) and return colour index
     * @param b
     * @param g
     * @param r
     * @return
     */
    int map(int b, int g, int r);
}
