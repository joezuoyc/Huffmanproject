
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Qiaoyi Fang
 * @author Joe Zuo
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if(bits!= HUFF_TREE) {
			throw new HuffException("illegal header starts with"+bits);
		}
		if(bits==-1) {
			throw new HuffException("illegal header starts with"+bits);
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
		/*
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();
		*/
	}
	
	public void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode tmp = root;
		int bits;
		while (true) {
			bits = in.readBits(1);
			if(bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			if(bits == 0) {
				tmp = tmp.myLeft;
			}
			else {
				tmp = tmp.myRight;
			}
			if(tmp.myLeft==null && tmp.myRight==null) {
				if(tmp.myValue==PSEUDO_EOF) break;
				else {
					int val = tmp.myValue;
					out.writeBits(BITS_PER_WORD, val);
					tmp = root;
				}
			}
		}
	}
	
	public HuffNode readTreeHeader(BitInputStream in) {
		int bits = in.readBits(1);
		if (bits == -1) {
            throw new HuffException("bad input, no PSEUDO_EOF");
        }
		HuffNode root = new HuffNode(0,0);
		if(bits == 0) {
			root.myLeft= readTreeHeader(in);
			root.myRight = readTreeHeader(in);
			return new HuffNode(0,0,root.myLeft,root.myRight);
		}
		else {
			int val = in.readBits(BITS_PER_WORD+1);
			return new HuffNode(val,0);
		}
		
	}
	
	public void compress(BitInputStream in, BitOutputStream out) {
		int[] conuts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.rest();
		writeCompressedBit(codings, in, out);
		out.close();
	}
	
	public int[] readForCounts(BitInputStream in) {
		int freq[ALPH_SIZE+1];
		
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) {
				freq[PSEUDO_EOF] = 1;
				break;
			}
			freq[val]++;
		}
		
		return freq;
	}
	
	public HuffNode makeTreeFromCounts(int[] freq) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for (int i=0; i<=ALPH_SIZE; i++) {
			if (freq[i]) {
				pq.add(new HuffNode(i, freq[index], null, null));
			}
		}
		
		while(pq.size()>1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(null, left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		
		HuffNode root = pq.remove();
	}
	
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myValue != null) { // or root.myLeft==null&&root.myRight==null
			encodings[root.myValue] = path;
			return;
		}
		else {
			if (root.myLeft != null) {
				codingHelper(root.myLeft, path+"0", encodings);
				}
			if (root.myRight != null) {
				codingHelper(root.myRight, path+"1", encodings);
			}
		}
		return;
	}
	
	public String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings; //not sure whether encodings is global invariable and can be updated in codingHelper
	}
	
	public void writeHeader(HuffNode root, BitOutputStream out) {
		if (root.myValue != null) { //or root.myLeft==null&&root.myRight==null
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);	
		}
		else {
			out.writeBits(1, 0);
		}
		
		if (root.myLeft!=null) {
			writeHeader(root.myLeft, out);
		}
		if (root.myRight!=null) {
			writeHeader(root.myRight, out);
		}
		
		return;
	}
	
	public void writeCompressedBit(String[] codings, BitInputStream in, BitOutputStream out)
	{
		in.reset();
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			String code = encodings[val];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
			if (val == PSEUDO_EOF)
				break;
		}
		return;
	}
}