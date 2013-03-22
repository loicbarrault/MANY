/*
 * Copyright 2009 Loic BARRAULT.  
 * Portions Copyright BBN and UMD (see LICENSE_TERP.txt).  
 * Portions Copyright 1999-2008 CMU (see LICENSE_SPHINX4.txt).
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "LICENSE.txt" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */
package edu.lium.decoder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Vector;
import java.util.regex.Pattern;
public class Graph
{
	public static final String null_node = "NULL";
	public static final int null_node_id = 0;
	protected final String node_prefix = "I=";
	protected final String link_prefix = "J=";

	protected static final String CN_NAME_PREFIX = "name";
	protected static final String CN_NUMALIGNS_PREFIX = "numaligns";
	protected static final String CN_ALIGN_PREFIX = "align";
	protected static final String CN_SYS_WEIGHTS_PREFIX = "sysweights";
	protected static final String CN_NBSYS_PREFIX = "nbsys";
	protected static final String CN_NUMSYS_PREFIX = "numsys";

	public Vector<Node> nodes = null;
	public Node firstNode = null, lastNode = null;
	Integer nbLinks;
	Integer ids; // for word ids
	Integer nodeIds, linkIds;
	Hashtable<String, Integer> wordToId = null;
	Hashtable<Integer, String> idToWord = null;
	Hashtable<Float, Node> timeToNode = null;
	Hashtable<Integer, Node> idToNode = null;
	float[] costs = null;
	float[] sys_weights = null;
	
	public float getCost(int i)
	{
		return costs[i];
	}

	public Graph()
	{
		init();
	}

	public Graph(ArrayList<ArrayList<ArrayList<Comparable<String>>>> aligns,
			ArrayList<ArrayList<ArrayList<Float>>> aligns_scores, ArrayList<ArrayList<ArrayList<Integer>>> aligns_sysids, float[] priors, float[] costs)
	{
		init();
		this.costs = costs;
		this.sys_weights = priors;
		
		Node curNode;
		Vector<Node> precNodes = new Vector<Node>(aligns.size());
		int wID;
		float st = 0.0f;
		firstNode = createNode(st++);
		int pos = 0;
		boolean over = false;
		if (aligns.size() != aligns_scores.size() || aligns.size() != priors.length)
		{
			System.err.println("Graph() : number of systems different from number of scores ... exiting");
			return;
		}
		for (int sysid = 0; sysid < aligns.size(); sysid++) // foreach backbone system
		{
			if(aligns.get(sysid) != null)
			{
				curNode = createNode(st++);
				// link NULL word, probability of backbone
				ArrayList<Integer> sysids = new ArrayList<Integer>();
				sysids.add(sysid);
				createLink(firstNode, curNode, sys_weights[sysid], sys_weights[sysid], 0, sysids);
				// System.err.println("Le precNode du CN "+i+" devient "+curNode.time);
				precNodes.add(sysid, curNode);
			}
		}
		// int b = 0;
		while (!over)
		{
			// System.err.println("******boucle "+b); b++;
			over = true;
			int i = 0;
			for(int sysid=0; sysid<aligns.size(); sysid++) // foreach backbone system
			{
				ArrayList<ArrayList<Comparable<String>>> curCN = aligns.get(sysid);
				// System.err.println("***********system "+i+" precNode = "+precNodes.elementAt(i).time+" "+precNodes.size());
				if (curCN != null && pos < curCN.size())
				{
					ArrayList<ArrayList<Float>> curCN_scores = aligns_scores.get(sysid);
					ArrayList<ArrayList<Integer>> curCN_sysids = aligns_sysids.get(sysid);
					
					// System.err.println("*************il convient ");
					over = false;
					curNode = createNode(st++);
					// System.err.println("*************apres createNode pos = "+pos);
					// copy all nextLinks of CN between precNodes[i] and curNode
					// System.err.println("************* nb link = "+curGraph.nodes.elementAt(pos).nextLinks.size());
					
					ArrayList<Comparable<String>> mesh = curCN.get(pos);
					ArrayList<Float> mesh_scores = curCN_scores.get(pos);
					ArrayList<Integer> mesh_sysids = curCN_sysids.get(pos);
					
					for(int nw=0; nw < curCN.get(pos).size(); nw++)
					{
						// System.err.println("*************le link "+l);
						// System.err.println("*************le link : "+curGraph.idToWord.get(l.wordId);
						wID = createWord((String)mesh.get(nw));
						// System.err.println("Importation du mot "+curGraph.idToWord.get(l.wordId)+" donne ID="+wID);
						ArrayList<Integer> sysids = new ArrayList<Integer>();
						sysids.add(mesh_sysids.get(nw));
						createLink(precNodes.elementAt(i), curNode, mesh_scores.get(nw), mesh_scores.get(nw), wID, sysids);
						// System.err.println("sys "+i+" createLink : "+precNodes.elementAt(i).time+" "+curNode.time);
					}
					precNodes.removeElementAt(i);
					precNodes.add(i, curNode);
					// System.err.println("BOUCLE : Le precNode du CN " + i +
					// " devient " + curNode.time);
					i++;
				}
			}
			pos++;
			// if(over) System.err.println("on a fini !!");
		}
		lastNode = createNode(st);
		for (int sysid = 0; sysid < aligns.size(); sysid++) // foreach backbone system
		{
			if(aligns.get(sysid) != null)
			{
				// link NULL word, probability of backbone
				// System.err.println("-------le link : "+precNodes.elementAt(i).time+" <. "+lastNode.time);
				ArrayList<Integer> sysids = new ArrayList<Integer>();
				sysids.add(sysid);
				createLink(precNodes.elementAt(sysid), lastNode, 1.0f, 1.0f, 0, sysids);
			}
		}
		// System.err.println("Graph::Graph() : END");
	}
	public Graph(Vector<Graph> vec, Vector<Float> priors)
	{
		// System.err.println("Graph::Graph() : START");
		init();
		Node curNode;
		Vector<Node> precNodes = new Vector<Node>(vec.size());
		int wID;
		float st = 0.0f;
		firstNode = createNode(st++);
		int pos = 0;
		boolean over = false;
		if (vec.size() != priors.size())
		{
			System.err.println("Graph() : number of systems different from number of scores ... exiting");
			return;
		}
		for (int sysid = 0; sysid < priors.size(); sysid++) // foreach backbone system
		{
            if(vec.get(sysid) == null) continue;
			curNode = createNode(st++);
			// link NULL word, probability of backbone
			ArrayList<Integer> sysids = new ArrayList<Integer>();
			sysids.add(sysid);
			createLink(firstNode, curNode, priors.elementAt(sysid), priors.elementAt(sysid), 0, sysids);
			// System.err.println("Le precNode du CN "+i+" devient "+curNode.time);
			precNodes.add(sysid, curNode);
		}
		// int b = 0;
		while (!over)
		{
			// System.err.println("******boucle "+b); b++;
			over = true;
			int sysid = 0;
			for (Graph curGraph : vec) // foreach backbone system
			{
                if(curGraph == null) continue;
				// System.err.println("***********system "+i+" precNode = "+precNodes.elementAt(i).time+" "+precNodes.size());
				if (pos < curGraph.nodes.size() - 1)
				{
					// System.err.println("*************il convient ");
					over = false;
					curNode = createNode(st++);
					// System.err.println("*************apres createNode pos = "+pos);
					// copy all nextLinks of CN between precNodes[i] and curNode
					// System.err.println("************* nb link = "+curGraph.nodes.elementAt(pos).nextLinks.size());
					for (Link l : curGraph.nodes.elementAt(pos).nextLinks)
					{
						// System.err.println("*************le link "+l);
						// System.err.println("*************le link : "+curGraph.idToWord.get(l.wordId);
						wID = createWord(curGraph.idToWord.get(l.wordId));
						// System.err.println("Importation du mot "+curGraph.idToWord.get(l.wordId)+" donne ID="+wID);
						createLink(precNodes.elementAt(sysid), curNode, l.posterior, l.posterior, wID, l.sysids);
						// System.err.println("sys "+i+" createLink : "+precNodes.elementAt(i).time+" "+curNode.time);
					}
					precNodes.removeElementAt(sysid);
					precNodes.add(sysid, curNode);
					// System.err.println("BOUCLE : Le precNode du CN " + i +
					// " devient " + curNode.time);
					sysid++;
				}
			}
			pos++;
			// if(over) System.err.println("on a fini !!");
		}
		lastNode = createNode(st);
		for (int sysid = 0; sysid < priors.size(); sysid++) // foreach backbone system
		{
			// link NULL word, probability of backbone
			// System.err.println("-------le link : "+precNodes.elementAt(i).time+" <. "+lastNode.time);
			ArrayList<Integer> sysids = new ArrayList<Integer>();
			sysids.add(sysid);
			createLink(precNodes.elementAt(sysid), lastNode, 1.0f, 1.0f, 0, sysids);
		}
		// System.err.println("Graph::Graph() : END");
	}
	
	

	public Graph(ArrayList<MANYcn> cns, float[] priors)
	{
		// System.err.println("Graph::Graph(ArrayList<Graph>, double[]) : START");
		init();
		this.sys_weights = priors;
		
		Node curNode;
		Vector<Node> precNodes = new Vector<Node>(cns.size());
		int wID;
		float st = 0.0f;
		firstNode = createNode(st++);
		int pos = 0;
		boolean over = false;
		if (cns.size() != sys_weights.length)
		{
			System.err.println("Graph() : number of systems ("+cns.size()+") different from number of scores ("+sys_weights.length+") ... exiting");
			return;
		}
		for (int sysid = 0; sysid < sys_weights.length; sysid++) // foreach backbone system
		{
			curNode = createNode(st++);
			// link NULL word, probability of backbone
			ArrayList<Integer> sysids = new ArrayList<Integer>();
			sysids.add(sysid);
			createLink(firstNode, curNode, sys_weights[sysid], sys_weights[sysid], 0, sysids);
			// System.err.println("Le precNode du CN "+i+" devient "+curNode.time);
			precNodes.add(sysid, curNode);
		}
		// int b = 0;
		while (!over)
		{
			// System.err.println("******boucle "+b); b++;
			over = true;
			int sysid = 0;
			for (Graph curGraph : cns) // foreach backbone system
			{
				// System.err.println("***********system "+i+" precNode = "+precNodes.elementAt(i).time+" "+precNodes.size());
				if (pos < curGraph.nodes.size() - 1)
				{
					// System.err.println("*************il convient ");
					over = false;
					curNode = createNode(st++);
					// System.err.println("*************apres createNode pos = "+pos);
					// copy all nextLinks of CN between precNodes[i] and curNode
					// System.err.println("************* nb link = "+curGraph.nodes.elementAt(pos).nextLinks.size());
					for (Link l : curGraph.nodes.elementAt(pos).nextLinks)
					{
						// System.err.println("*************le link "+l);
						// System.err.println("*************le link : "+curGraph.idToWord.get(l.wordId);
						wID = createWord(curGraph.idToWord.get(l.wordId));
						//System.err.println("Importation du mot "+curGraph.idToWord.get(l.wordId)+" donne ID="+wID+" sysid="+TERutilities.join(" ",l.sysids));
						createLink(precNodes.elementAt(sysid), curNode, l.posterior, l.posterior, wID, l.sysids);
						// System.err.println("sys "+i+" createLink : "+precNodes.elementAt(i).time+" "+curNode.time);
					}
					precNodes.removeElementAt(sysid);
					precNodes.add(sysid, curNode);
					// System.err.println("BOUCLE : Le precNode du CN " + i +
					// " devient " + curNode.time);
					sysid++;
				}
			}
			pos++;
			// if(over) System.err.println("on a fini !!");
		}
		lastNode = createNode(st);
		for (int sysid = 0; sysid < sys_weights.length; sysid++) // foreach backbone system
		{
			// link NULL word, probability of backbone
			// System.err.println("-------le link : "+precNodes.elementAt(i).time+" <. "+lastNode.time);
			ArrayList<Integer> sysids = new ArrayList<Integer>();
			sysids.add(sysid);
			createLink(precNodes.elementAt(sysid), lastNode, 1.0f, 1.0f, 0, sysids);
		}
		// System.err.println("Graph::Graph() : END");
	}

	public void init()
	{
		// System.err.println("Graph::init START : ");

		// if (nodes != null) {
		// System.err.println("Graph:init : clearing nodes "); nodes.clear();}
		// else { System.err.println("Graph:init : allocating nodes "); nodes =
		// new Vector<Node>();}
		if (nodes != null)
		{
			nodes.clear();
		}
		else
		{
			nodes = new Vector<Node>();
		}

		firstNode = lastNode = null;
		nbLinks = 0;

		// if(wordsID != null) {
		// System.err.println("Graph:init : clearing wordsId ");
		// wordsID.clear(); }
		// else { System.err.println("Graph:init : allocating wordsId ");
		// wordsID = new Hashtable<String, Integer>(); }
		if (wordToId != null)
		{
			wordToId.clear();
		}
		else
		{
			wordToId = new Hashtable<String, Integer>();
		}

		// if(idToWord != null) {
		// System.err.println("Graph:init : clearing idToWord ");
		// idToWord.clear();}
		// else { System.err.println("Graph:init : allocating idToWord ");
		// idToWord = new Hashtable<Integer, String>(); }
		if (idToWord != null)
		{
			idToWord.clear();
		}
		else
		{
			idToWord = new Hashtable<Integer, String>();
		}

		ids = 0; // for word ids
		createWord(null_node);
		nodeIds = 0;

		// if(timeToNode != null) {
		// System.err.println("Graph:init : clearing timeToNode ");
		// timeToNode.clear();}
		// else { System.err.println("Graph:init : allocating timeToNode ");
		// timeToNode = new Hashtable<Float, Node>();}
		if (timeToNode != null)
		{
			timeToNode.clear();
		}
		else
		{
			timeToNode = new Hashtable<Float, Node>();
		}

		// if(idToNode != null) {
		// System.err.println("Graph:init : clearing idToNode ");
		// idToNode.clear(); }
		// else { System.err.println("Graph:init : allocating idToNode ");
		// idToNode = new Hashtable<Integer, Node>(); }
		if (idToNode != null)
		{
			idToNode.clear();
		}
		else
		{
			idToNode = new Hashtable<Integer, Node>();
		}

		linkIds = 0;
		nbLinks = 0;
		// System.err.println("Graph END");
	}

	protected Link createLink(Node startNode, Node endNode, float likelihood, float probability, int wID, ArrayList<Integer> sysids)
	{
		nbLinks++;
		return new Link(startNode, endNode, likelihood, probability, wID, sysids, linkIds++);
	}
	
	protected Link createUniqueLink(Node startNode, Node endNode, float probability, float posterior, String w, ArrayList<Integer> sysids)
	{
		int wID = createWord(w);
		return createUniqueLink(startNode, endNode, probability, wID, sysids);
	}
	
	protected Link createUniqueLink(Node startNode, Node endNode, float probability, int wID, ArrayList<Integer> sysids)
	{
		for(Link l : startNode.nextLinks)
		{
			//if a link as same startNode and endNode, and same word, then just sum up the probs.
			if(l.endNode == endNode && l.wordId == wID)
			{
				l.posterior += probability;
				l.probability += probability;
				l.addSysIDs(sysids);
				return l;
			}
		}
		//otherwise, create a new link as usual
		return createLink(startNode, endNode, probability, probability, wID, sysids);
	}
	protected Link createUniqueLink(Node startNode, Node endNode, float probability, float posterior, int wID, ArrayList<Integer> sysids)
	{
		System.err.println("Creating unique link : from "+startNode.time+" to "+endNode.time+" w="+idToWord.get(wID)+" (id="+wID+")");
		for(Link l : startNode.nextLinks)
		{
			System.err.println("Link observed : "+l.toString());
			//if a link as same startNode and endNode, and same word, then just sum up the probs.
			if(l.endNode == endNode && l.wordId == wID)
			{
				l.posterior += posterior;
				l.probability += probability;
				l.addSysIDs(sysids);
				return l;
			}
		}
		//otherwise, create a new link as usual
		System.err.println("Creating new Link ... ");
		return createLink(startNode, endNode, probability, posterior, wID, sysids);
	}
	
	
	
	protected void deleteLink(Link l)
	{
		nbLinks--;
		l = null;
	}
	protected int createWord(String w)
	{
		//System.err.print("Creating word >"+w+"<");
		Integer wID;
		if (wordToId.containsKey(w)) // the word is known, get its ID
		{
			//System.err.println(" : already existing !");
			wID = wordToId.get(w); // on recupere son id
		}
		else
		{
			//System.err.println(" : a brand new one !");
			String s = new String(w);
			Integer i = new Integer(ids);
			wordToId.put(s, i);
			wID = i;
			idToWord.put(i, s);
			ids++;
		}
		return wID;
	}

	protected Node createNode(float time)
	{
		Node node = null;
		if (timeToNode.containsKey(time))
		{
			node = timeToNode.get(time);
			// System.err.println("Graph::createNode : on connait le node "+time);
		}
		else
		{
			// System.err.println("Graph::createNode : Ajout du node id="+nodeIds+" t="+time);
			node = new Node(nodeIds, time);
			timeToNode.put(time, node);
			idToNode.put(nodeIds, node);
			nodes.add(node);
			nodeIds++;
		}
		return node;
	}

	public void readHTK(String file)
	{
		String line = null;
		// cerr << "********readHTK : START" << endl;
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(file));
			while ((line = reader.readLine()) != null)
			{
				if (line.length() > 0)
				{
					if (line.startsWith(node_prefix))
					{
						parseNode(line);
					}
					else if (line.startsWith(link_prefix))
					{
						parseLink(line);
					}
					else
					{
						// System.err.println("Ignoring line >" + line + "<");
					}
				}
			}
		}
		catch (IOException ioe)
		{
			System.err.println("I/O erreur durant file" + file + " " + ioe);
		}
		// cerr << "********load : END" << endl;
	}
	
	public String getHTKString()
	{
		StringBuilder s = new StringBuilder("# Header\n");
		s.append("VERSION=1.0\nUTTERANCE=utt_id\n#Size definition\n");
		s.append("N=").append(nodes.size()).append("\tL=").append(nbLinks).append("\n").append("# Node definitions\n");
		for (Node n : nodes)
		{
			s.append("I=").append(n.id).append("\tt=").append(n.time).append("\n");
		}
		s.append("# Link definitions\n");
		int j = 0;
		for (Node n : nodes)
		{
			for (Link l : n.nextLinks)
			{
				s.append("J=").append(j).append("\tS=").append(l.startNode.id).append("\tE=").append(l.endNode.id).append("\tW=")
				.append(idToWord.get(l.wordId)).append("\ta=").append(l.posterior).append("\n");
				j++;
			}
		}
		return s.toString();
	}
	
	public void printHTK(String fileOut)
	{
		BufferedWriter writer = null;
		try
		{
			writer = new BufferedWriter(new FileWriter(fileOut));
			writer.write(getHTKString());
			/*writer.write("# Header");
			writer.newLine();
			writer.write("VERSION=1.0");
			writer.newLine();
			writer.write("UTTERANCE=utt_id");
			writer.newLine();
			writer.write("#Size definition");
			writer.newLine();
			writer.write("N=" + nodes.size() + "\tL=" + nbLinks);
			writer.newLine();
			writer.write("# Node definitions");
			writer.newLine();
			for (Node n : nodes)
			{
				writer.write("I=" + n.id + "\tt=" + n.time);
				writer.newLine();
			}
			writer.write("# Link definitions");
			writer.newLine();
			int j = 0;
			for (Node n : nodes)
			{
				for (Link l : n.nextLinks)
				{
					writer.write("J=" + j + "\tS=" + l.startNode.id + "\tE=" + l.endNode.id + "\tW="
							+ idToWord.get(l.wordId) + "\ta=" + l.posterior);
					writer.newLine();
					j++;
				}
			}*/
			writer.close();
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace();
		}
	}
	private void parseNode(String line)
	{
		// System.err.println("********parseNode : START : >"+line+"<");
		//Double posterior = 0.0;
		Float time = 0.0f;
		Pattern m = Pattern.compile("\\t");
		Pattern c = Pattern.compile("=");
		String fields[] = m.split(line);

		for (int i = 0; i < fields.length; i++)
		{
			// System.err.println("********parseNode : field["+i+"] : >"+fields[i]+"<");
			String t[] = c.split(fields[i]);
			if ("P".equals(t[0]) || "p".equals(t[0]))
			{
				//posterior = Double.parseDouble(t[1]);
			}
			else if ("T".equals(t[0]) || "t".equals(t[0]))
			{
				time = Float.parseFloat(t[1]);
			}
		}
		Node n = createNode(time);
		if (firstNode == null)
			firstNode = n;
		lastNode = n;
	}
	private void parseLink(String line)
	{
		// System.err.println("********parseLink : START : >"+line+"<");

		String word;
		int startNode = 0, endNode = 0;
		float lmProb = 1;
		float prob = lmProb;
		Integer wID = -1; //, linkId;

		if (line == null || line.equals(""))
			return;

		Pattern m = Pattern.compile("\\t");
		Pattern c = Pattern.compile("=");
		String fields[] = m.split(line);

		for (int i = 0; i < fields.length; i++)
		{
			String t[] = c.split(fields[i]);
			if ("J".equals(t[0]) || "j".equals(t[0]))
			{
				// = Integer.parseInt(t[1]);
			}
			else if ("S".equals(t[0]) || "s".equals(t[0]))
			{
				startNode = Integer.parseInt(t[1]);
			}
			else if ("E".equals(t[0]) || "e".equals(t[0]))
			{
				endNode = Integer.parseInt(t[1]);
			}
			else if ("A".equals(t[0]) || "a".equals(t[0]))
			{
				prob = Float.parseFloat(t[1]);
			}
			else if ("W".equals(t[0]) || "w".equals(t[0]))
			{
				word = t[1];
				wID = createWord(word);
			}
		}

		// System.err.println(""+startNode+" -> "+idToNode.get(startNode).time+" et "+endNode+" -> "+idToNode.get(endNode).time);

		if (idToNode.get(startNode).time != idToNode.get(endNode).time)
		{
			createLink(idToNode.get(startNode), idToNode.get(endNode), prob, prob, wID, null);
		}
		else
		{
			System.err.println(idToNode.get(startNode).time + " == " + idToNode.get(endNode).time
					+ " : aucun Link cree\n\t pour la ligne " + line);
		}
		// System.err.println("********parseLink : END");
	}
	
	public String getWordFromId(Integer id)
	{
		return idToWord.get(id);
	}
	
	
}
