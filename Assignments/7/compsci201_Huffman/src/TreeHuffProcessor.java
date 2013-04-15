import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.PriorityQueue;

public class TreeHuffProcessor implements IHuffProcessor {
    
    private HuffViewer myViewer;
    private TreeNode myRoot; 
    private HashMap<Integer, String> myMap; 
    private Integer mySize; 
    private int[] myCounts = new int[256]; 
    
    public int compress(InputStream in, OutputStream out, boolean force) throws IOException {
    	// write the magic number
    	BitOutputStream bout = new BitOutputStream(out); 
    	bout.writeBits(BITS_PER_INT, MAGIC_NUMBER); 
    	
    	// write info that allows tree to be recreated
    	writeTraversal(myRoot, bout); 
    	
    	// write bits needed to encode each character of input file 
    	BitInputStream binput = new BitInputStream(in);
        int next = 1;
        while(next > 0){
        	next = binput.read(); 
        	String encoding = myMap.get(next);
        	for(int i=0; i<encoding.length(); i++){
        		char c = encoding.charAt(i);
        		if(c=='0'){ bout.writeBits(1, 0);}
        		else if(c=='1'){ bout.writeBits(1, 1); } 
        	}
        }
        binput.close(); 
        
        bout.writeBits(BITS_PER_INT, PSEUDO_EOF);
    	bout.close(); 
    	
    	return 0; 
    }
    
    public void writeTraversal(TreeNode t, BitOutputStream out){
    	if(t.isLeaf()){
    		out.writeBits(1, 1); // maybe BITS_PER_INT
    		out.writeBits(9, t.myValue);
    	}
    	else{
    		out.writeBits(1, 0); // maybe BITS_PER_INT
    		writeTraversal(t.myLeft, out);
    		writeTraversal(t.myRight, out); 
    	}
    }

    public int preprocessCompress(InputStream in) throws IOException {
    	// create forest of nodes
    	HashMap<Integer, TreeNode> forest = new HashMap<Integer, TreeNode>(); 
    	BitInputStream binput = new BitInputStream(in);
        int next = 1;
        while(next > 0){
        	next = binput.read(); 
        	if(forest.containsKey(next)){
        		TreeNode node = forest.get(next);
        		node.myWeight++;
        		forest.put(next, node);
        	}
        	else{
        		TreeNode node = new TreeNode(next, 1); 
        		forest.put(next, node);
        	}
        }
        binput.close(); 
        
        // create list of weights
        for(TreeNode t: forest.values()){
        	int v = t.myValue;
        	int w = t.myWeight;
        	if(v>0){
        		myCounts[v] = w; 
        	}
        }
        
        // turn forest into a single tree
        PriorityQueue<TreeNode> pq = new PriorityQueue<TreeNode>(forest.values()); 
        TreeNode nodeEof = new TreeNode(PSEUDO_EOF, 1); 
        pq.add(nodeEof); 
        myRoot = qShrinker(pq); 
        
        //create map of ints to encodings
        myMap = new HashMap<Integer, String>(); 
        mySize=0; 
        encodePaths(myRoot, ""); 
        
        int bitsSaved = myRoot.myWeight*8 - mySize;
        
        if(bitsSaved<0){ 
        	String e = String.format("compression uses %d more bits\n use force compression to compress", bitsSaved);
        	myViewer.showError(e);
        }
        
        return bitsSaved; 
    }
    
    public TreeNode qShrinker(PriorityQueue<TreeNode> q){
    	TreeNode tree; 
    	if(q.size()==1){
    		tree = q.poll(); 
    	}
    	else{
    		TreeNode smallest = q.remove();
    		TreeNode nextSmallest = q.remove();
    		TreeNode newNode = new TreeNode(smallest.myValue, nextSmallest.myWeight+smallest.myWeight, smallest, nextSmallest);
    		q.add(newNode); 
    		tree = qShrinker(q); 
    	}
    	return tree; 
    }
    
    public void encodePaths(TreeNode t, String path){
    	if(t.isLeaf()){
    		myMap.put(t.myValue, path); 
    		mySize += t.myWeight*path.length(); 
    		return; 
    	}
    	else{
    		encodePaths(t.myLeft, path + "0");
    		encodePaths(t.myRight, path + "1");
    	}
    }

    public void setViewer(HuffViewer viewer) {
        myViewer = viewer;
    }

    public int uncompress(InputStream in, OutputStream out) throws IOException {

        BitInputStream binput = new BitInputStream(in); 
        BitOutputStream bout = new BitOutputStream(out); 
    	
        // read magic number
        int magic = binput.readBits(BITS_PER_INT);
        if (magic != MAGIC_NUMBER){
        	binput.close(); 
        	bout.close();
            throw new IOException("magic number not right");
        }
        else{ System.out.println("magic number right"); }

        // read in encoding table
        myRoot = readTraversal(binput); 
        System.out.println("weight: " + myRoot.myWeight);
        
        // read remaining bits, map them, and write them out 
        int inbits;
        TreeNode node = myRoot; 
        while (true){
        	inbits = binput.readBits(1); 
            if (inbits == -1){
                System.err.println("should not happen! trouble reading bits");
                break; 
            }
            else{ 
                if ( (inbits & 1) == 0){ node = node.myLeft; } 
                else{ node = node.myRight;}                  

                if (node.isLeaf()){
                    if (node.myValue== PSEUDO_EOF){
                    	break; 
                    }
                    else{
                    	bout.writeBits(BITS_PER_INT, node.myValue);
                    	node = myRoot;
                    }    
                }
            }
        }
        
        binput.close();
        bout.close(); 
        return 0; 
    }
    
    public TreeNode readTraversal(BitInputStream in) throws IOException{
    	int bits = in.readBits(1); 
    	TreeNode node; 
    	if(bits==0){  // non-leaf
    		TreeNode left = readTraversal(in); 
        	TreeNode right = readTraversal(in); 
        	node = new TreeNode(left.myValue, left.myWeight+right.myWeight, left, right);
    	} 
    	else{ // reached leaf
    		bits = in.readBits(9);
    		node = new TreeNode(bits, 1);
    	} 
    	
    	return node; 
    }
    
    private void showString(String s){
        myViewer.update(s);
    }

}