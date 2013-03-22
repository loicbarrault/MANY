package edu.lium.decoder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import edu.lium.utilities.MANYutilities;

public class MANYcn extends Graph
{
	public int getNbMesh(){	return nbMesh; }
	public int getSize(){ return nbMesh; }
	public int getLength(){	return nbMesh; }

	private int nbMesh = 0;
	private int numSys = 0;
	private float[] sysWeights = null;
	
	public MANYcn()
	{
		super();
	}
	
	public MANYcn(ArrayList<ArrayList<Comparable<String>>> cn, ArrayList<ArrayList<Float>> cn_scores, ArrayList<ArrayList<Integer>> cn_sysids)
	{
		super();
		
		if(cn == null || cn_scores == null || cn.size() != cn_scores.size())
		{
			return;
		}
		
		float t = 0.0f;
		firstNode= createNode(t++);
		Node endNode = firstNode;
		Node startNode = null;
		int wID;
		
		nbMesh = cn.size();
		
		for(int i=0; i<cn.size(); i++)
		{
			//create endNode
			startNode = endNode;
			endNode = createNode(t++);
		
			for(int j=0; j<cn.get(i).size(); j++)
			{
				//create word
				wID = createWord(cn.get(i).get(j).toString());
				
				//make link between firstNode and endNode
				if(cn_sysids != null)
				{
					ArrayList<Integer> sysids = new ArrayList<Integer>();
					sysids.add(cn_sysids.get(i).get(j));
					createLink(startNode, endNode, cn_scores.get(i).get(j), cn_scores.get(i).get(j), wID, sysids);
				}
				else
				{
					createLink(startNode, endNode, cn_scores.get(i).get(j), cn_scores.get(i).get(j), wID, null);
				}
				
			}
		}
		lastNode = endNode;
	}
	
	/**
	 * changeSysWeights : change the sysweights of all the CNs and update them
	 * @param cns : the list of MAYcns to modify
	 * @param sw : the weights
	 */
	public static void changeSysWeights(ArrayList<MANYcn> cns, float[] sw)
	{
		//System.err.println("********MANYcn::changeCNWeights : START");
		//int i=0;
		for(MANYcn cn : cns)
		{
			//System.err.println("MANYcn::changeCNWeights : cn number "+i++);
			if(cn == null)
			{
				System.err.println("MANYcn::changeSysWeights : CN is null ...");
                return;
				//System.exit(0);
			}
			cn.changeSysWeights(sw);
		}
		//System.err.println("********MANYcn::changeCNWeights : END");
	}
	
	public void changeSysWeights(float[] sw)
	{
		if(sysWeights == null)
		{
			sysWeights = new float[sw.length];
		}
		else if(sysWeights.length != sw.length)
		{
			System.err.println("MANYcn::changeSysWeights : wrong number of weights "+sysWeights.length+" != "+sw.length+" ... skipping !");
			return;
		}
		for(int i=0; i<sysWeights.length; i++)
		{
			sysWeights[i] = sw[i];
		}
		
		//System.err.println("********MANYcn::changeSysWeights : START");
		ArrayList<Node> curNodes = new ArrayList<Node>();
		ArrayList<Node> nextNodes = new ArrayList<Node>();
		curNodes.add(firstNode);
		
		while(curNodes.isEmpty() == false)
		{
			Node curNode = curNodes.get(0);
			curNodes.remove(0);
			for(int i=0; i<curNode.nextLinks.size(); i++)
			{
				if(curNode.nextLinks.get(i).sysids != null && curNode.nextLinks.get(i).sysids.get(0) != -1)
				{
					for (int sysid : curNode.nextLinks.get(i).sysids)
					{
						curNode.nextLinks.get(i).posterior = curNode.nextLinks.get(i).probability = sysWeights[sysid];
					}
				}
				if(curNode.nextLinks.get(i).endNode != lastNode && nextNodes.contains(curNode.nextLinks.get(i).endNode) == false)
					nextNodes.add(curNode.nextLinks.get(i).endNode);
			}
			
			if(curNodes.isEmpty() && !nextNodes.isEmpty())
			{
				ArrayList<Node> tmp = curNodes;
				curNodes = nextNodes;
				nextNodes = tmp;
			}
		}
		//System.err.println("********MANYcn::toFullCNString : END");
	}
	
	
	/**
	 * loadFullCNs : read a file containing several full CNs
	 * Note : a full CN is a CN where 2 arcs from 2 different systems in a mesh are not merged (useful for rescoring the CN)
	 * @param file : input file
	 * @return ArrayList<MANYcn> : the list of full CNs
	 */
	public static ArrayList<MANYcn> loadFullCNs(String file)
	{
		boolean showLines = false;
		String line = null;
		float score = 0.0f;
		int alignid = -1, numaligns = 0;
		int nbSys = -1, numSys = -1;
		int wID, sysid = -1;
		float t = 0.0f;
		Node startNode = null;
		Node endNode  = null;
		MANYcn g = null;
		ArrayList<MANYcn> graphlist = new ArrayList<MANYcn>();
		// System.err.println("********MANYcn::loadFullCNs : START");
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(file));
			line = reader.readLine();
			while (line != null)
			{
				if(showLines) System.err.print("LINE : "+line);
				if (line.length() > 0)
				{
					if (line.startsWith(CN_NAME_PREFIX))
					{
						if(showLines) System.err.println(" -> NAME !");
						if(g != null)
						{
							// end of previous graph
							g.lastNode = endNode;
							
							if(numaligns != (g.nodes.size()-1))
							{
								System.err.println("MANYcn::loadFullCNs : WARNING, numaligns(="+numaligns+") != nbMesh(="+g.nbMesh+") != nbNodes-1(="+(g.nodes.size()-1)+")");
							}
							
						}
						// create new graph
						g = new MANYcn();
						graphlist.add(g);
						
						t = 0.0f;
						g.firstNode = g.createNode(t++);
						endNode = g.firstNode;
					}
					else if (line.startsWith(CN_NUMALIGNS_PREFIX))
					{
						if(showLines) System.err.println(" -> NUMALIGNS !");
						StringTokenizer st = new StringTokenizer(line);
						String s = st.nextToken();
						if (s.equals(CN_NUMALIGNS_PREFIX) == false)
						{
							System.err.println("Not a good '"+CN_NUMALIGNS_PREFIX+"' line ... exiting ! s="+s);
							System.exit(0);
						}
						
						s = st.nextToken();
						numaligns = Integer.parseInt(s);
					}
					else if (line.startsWith(CN_NUMSYS_PREFIX))
					{
						if(showLines) System.err.println(" -> NUMSYS !");
						StringTokenizer st = new StringTokenizer(line);
						String s = st.nextToken();
						if (s.equals(CN_NUMSYS_PREFIX) == false)
						{
							System.err.println("Not a good '"+CN_NUMSYS_PREFIX+"' line ... exiting ! s="+s);
							System.exit(0);
						}
						
						s = st.nextToken();
						g.numSys = Integer.parseInt(s); 
					}
					else if (line.startsWith(CN_NBSYS_PREFIX))
					{
						if(showLines) System.err.println(" -> NBSYS !");
						StringTokenizer st = new StringTokenizer(line);
						String s = st.nextToken();
						if (s.equals(CN_NBSYS_PREFIX) == false)
						{
							System.err.println("Not a good '"+CN_NBSYS_PREFIX+"' line ... exiting ! s="+s);
							System.exit(0);
						}
						
						s = st.nextToken();
						nbSys = Integer.parseInt(s); 
					}
					else if (line.startsWith(CN_SYS_WEIGHTS_PREFIX))
					{
						if(showLines) System.err.println(" -> SYSWEIGHTS !");
						if(nbSys == -1)
						{
							System.err.println("MANYcn::loadFullCNs : NBSYS not set before get weights ... exiting !");
							System.exit(0);
						}
						
						StringTokenizer st = new StringTokenizer(line);
						String s = st.nextToken();
						if (s.equals(CN_SYS_WEIGHTS_PREFIX) == false)
						{
							System.err.println("Not a good '"+CN_SYS_WEIGHTS_PREFIX+"' line ... exiting ! s="+s);
							System.exit(0);
						}
						//TODO : should verify that nbSys and sysWeight length are identical
						g.sysWeights = new float[nbSys];
						int i = 0;
						while (st.hasMoreTokens())
						{
							s = st.nextToken();
							g.sysWeights[i++] = Float.parseFloat(s); 
						}
					}
					else if (line.startsWith(CN_ALIGN_PREFIX))
					{
						if(showLines) System.err.println(" -> ALIGN !");
						startNode = endNode;
						endNode = g.createNode(t++);
						g.nbMesh++;
						
						StringTokenizer st = new StringTokenizer(line);
						String s = st.nextToken();
						if (s.equals(CN_ALIGN_PREFIX) == false)
						{
							System.err.println("Not a good 'align' line ... exiting ! s="+s);
							System.exit(0);
						}
						s = st.nextToken();
						try
						{
							alignid = Integer.parseInt(s);
						}
						catch (NumberFormatException nfe)
						{
							nfe.printStackTrace();
						}

						while (st.hasMoreTokens())
						{
							// one word one score one sysid .. etc
							s = st.nextToken();
							wID = g.createWord(s);
							s = st.nextToken();
							try
							{
								score = Float.parseFloat(s);
							}
							catch (NumberFormatException nfe)
							{
								nfe.printStackTrace();
							}
							s = st.nextToken();
							try
							{
								sysid = Integer.parseInt(s);
							}
							catch (NumberFormatException nfe)
							{
								nfe.printStackTrace();
							}
							
							ArrayList<Integer> sysids = new ArrayList<Integer>();
							sysids.add(sysid);
							g.createLink(startNode, endNode, score, score, wID, sysids);
						}
					}
					else
					{
						if(showLines) System.err.println(" -> IGNORE !");
						//System.err.println("MANYcn::loadFullCNs : Ignoring line >" + line + "<");
					}
				}
				else
				{
					if(showLines) System.err.println(" -> EMPTY !");
				}
				line = reader.readLine();
			}
			
			if(g != null)
			{
				// end of previous graph
				g.lastNode = endNode;
				
				if(numaligns != (g.nodes.size()-1))
				{
					System.err.println("MANYcn::loadFullCNs : WARNING, numaligns(="+numaligns+") != nbMesh(="+g.nbMesh+") != nbNodes-1(="+(g.nodes.size()-1)+")");
				}
			}
		}
		catch (IOException ioe)
		{
			System.err.println("MANYcn::loadFullCNs : I/O error when reading file" + file + " " + ioe);
			ioe.printStackTrace();
		}
		
		return graphlist;
		// System.err.println("********MANYcn::loadFullCNs : END");
	}
	
	/**
	 * outputCNs : create a file containing several CNs
	 * @param cns : the list of CNs to output
	 * @param file : output file
	 */
	public static void outputCNs(ArrayList<MANYcn> cns, String file)
	{
		//System.err.println("********MANYcn::outputFullCNs : START");
		try
		{
			BufferedWriter writer = new BufferedWriter(new FileWriter(file));
			//int i=0;
			for(MANYcn cn : cns)
			{
				//System.err.println("MANYcn::outputFullCNs : cn number "+i++);
				if(cn == null)
				{
					System.err.println("MANYcn::outputFullCNs : CN is null ... exiting !");
					System.exit(0);
				}
				
				String str = cn.toCNString();
				//System.err.println("MANYcn::outputFullCNs : CN string is ");
				//System.err.println(str);
				writer.write(str);
				writer.newLine();
			}
			writer.close();
		}
		catch (IOException ioe)
		{
			System.err.println("MANYcn::outputFullCNs : I/O error when reading file" + file + " " + ioe);
			ioe.printStackTrace();
		}
		//System.err.println("********MANYcn::outputFullCNs : END");
	}
	
	/**
	 * outputFullCNs : create a file containing several full CNs
	 * Note : a full CN is a CN where 2 arcs from 2 different systems in a mesh are not merged (useful for rescoring the CN)
	 * @param cns : the list of full CNs to output
	 * @param file : output file
	 */
	public static void outputFullCNs(ArrayList<MANYcn> cns, String file)
	{
		//System.err.println("********MANYcn::outputFullCNs : START");
		try
		{
			BufferedWriter writer = new BufferedWriter(new FileWriter(file));
			//int i=0;
			for(MANYcn cn : cns)
			{
				//System.err.println("MANYcn::outputFullCNs : cn number "+i++);
				if(cn == null)
				{
					System.err.println("MANYcn::outputFullCNs : CN is null ... skipping !");
					continue;
					//System.exit(0);
				}
				
				String str = cn.toFullCNString();
				//System.err.println("MANYcn::outputFullCNs : CN string is ");
				//System.err.println(str);
				writer.write(str);
				writer.newLine();
			}
			writer.close();
		}
		catch (IOException ioe)
		{
			System.err.println("MANYcn::outputFullCNs : I/O error when reading file" + file + " " + ioe);
			ioe.printStackTrace();
		}
		//System.err.println("********MANYcn::outputFullCNs : END");
	}
	
	
	/**
	 * loadFullCN : load a file containing a single full CN
	 * Note : a full CN is a CN where 2 arcs from 2 different systems in a mesh are not merged (useful for rescoring the CN)
	 * @param file : input file
	 */
	public void loadFullCN(String file)
	{
		String line = null;
		float score = 0.0f;
		int alignid, wID, sysid=-1;
		float t = 0.0f;
		Node startNode = null; //createNode(t);
		firstNode= createNode(t++);
		Node endNode  = firstNode;
		
		// cerr << "********readFullCN : START" << endl;
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(file));
			while ((line = reader.readLine()) != null)
			{
				if (line.length() > 0)
				{
					if (line.startsWith(CN_NAME_PREFIX))
					{
					}
					else if (line.startsWith(CN_NUMALIGNS_PREFIX))
					{
					}
					else if (line.startsWith(CN_ALIGN_PREFIX))
					{
						startNode = endNode;
						endNode = createNode(t++);
						
						StringTokenizer st = new StringTokenizer(line);
						
						if(	(st.countTokens()-2) %3 != 0)
						{
							System.err.println("This seems not to be a FULLCN ... exiting ! line="+line);
							System.err.println("FULLCN format is the following : <align> <alignid> <word> <prob> <sysid>");
							System.exit(0);
						}
						
						String s = st.nextToken();
						if (s.equals("align") == false)
						{
							System.exit(0);
						}
						s = st.nextToken();
						try
						{
							alignid = Integer.parseInt(s);
						}
						catch (NumberFormatException nfe)
						{
							nfe.printStackTrace();
						}

						while (st.hasMoreTokens())
						{
							// one word one score one sysid .. etc
							s = st.nextToken();
							wID = createWord(s);
							s = st.nextToken();
							try
							{
								score = Float.parseFloat(s);
							}
							catch (NumberFormatException nfe)
							{
								nfe.printStackTrace();
							}
							s = st.nextToken();
							try
							{
								sysid = Integer.parseInt(s);
							}
							catch (NumberFormatException nfe)
							{
								nfe.printStackTrace();
							}
							
							ArrayList<Integer> sysids = new ArrayList<Integer>();
							sysids.add(sysid);
							createLink(startNode, endNode, score, score, wID, sysids);
						}
					}
					else
					{
						// System.err.println("Ignoring line >" + line + "<");
					}
				}
			}
			reader.close();
		}
		catch (IOException ioe)
		{
			System.err.println("I/O error when reading file" + file + " " + ioe);
			ioe.printStackTrace();
		}
		
		lastNode = endNode;
		// cerr << "********readFullCN : END" << endl;
	}
	
	public String toFullCNString()
	{
		//System.err.println("********MANYcn::toFullCNString : START");
		StringBuilder s = new StringBuilder("name cn1best\nnumaligns ");
		s.append(nodes.size()-1).append("\nnumsys ").append(numSys).append("\nnbsys ");
		if(sysWeights != null) s.append(sysWeights.length);
		else s.append(0);
		s.append("\nsysweights");
		for(int i=0; i<sysWeights.length; i++)
			s.append(" "+sysWeights[i]);
		s.append("\n\n");
		
		ArrayList<Node> curNodes = new ArrayList<Node>();
		ArrayList<Node> nextNodes = new ArrayList<Node>();
		curNodes.add(firstNode);
		int alNum = 0;
		s.append("align ").append(alNum++).append(" ");
		
		while(curNodes.isEmpty() == false)
		{
			Node curNode = curNodes.get(0);
			curNodes.remove(0);
			
			if(curNode.nextLinks == null)
			{
				s.append("\n");
				continue;
			}
			
			for(int i=0; i<curNode.nextLinks.size(); i++)
			{
				s.append(idToWord.get(curNode.nextLinks.get(i).wordId));
				s.append(" ");
				s.append(curNode.nextLinks.get(i).posterior);
				s.append(" ");
				s.append(curNode.nextLinks.get(i).sysids.get(0));
				s.append(" ");
				
				if(curNode.nextLinks.get(i).endNode != lastNode && nextNodes.contains(curNode.nextLinks.get(i).endNode) == false)
					nextNodes.add(curNode.nextLinks.get(i).endNode);
			}
			s.append("\n");
			
			if(curNodes.isEmpty() && !nextNodes.isEmpty())
			{
				/*if(nextNodes.get(0).nextLinks.size() == 0)
				{
					System.err.println("MANYcn::toFullCNString : nextNodes contains a node with nextLinks.size() == 0");
					System.err.println("Node : "+nextNodes.get(0).toString());
					System.err.println("Node : "+lastNode.toString());
				}*/
				s.append("align ").append(alNum++).append(" ");
				ArrayList<Node> tmp = curNodes;
				curNodes = nextNodes;
				nextNodes = tmp;
			}
		}
		//System.err.println("********MANYcn::toFullCNString : END");
		return s.toString();
	}
	
	public String toCNString()
	{
		//System.err.println("********MANYcn::toFullCNString : START");
		StringBuilder s = new StringBuilder("name cn1best\nnumaligns ");
		s.append(nodes.size()-1);
		s.append("\n\n");
		
		ArrayList<Node> curNodes = new ArrayList<Node>();
		ArrayList<Node> nextNodes = new ArrayList<Node>();
		curNodes.add(firstNode);
		int alNum = 0;
		s.append("align ").append(alNum++).append(" ");
		
		while(curNodes.isEmpty() == false)
		{
			Node curNode = curNodes.get(0);
			curNodes.remove(0);
			for(int i=0; i<curNode.nextLinks.size(); i++)
			{
				s.append(idToWord.get(curNode.nextLinks.get(i).wordId));
				s.append(" ");
				s.append(curNode.nextLinks.get(i).posterior);
				s.append(" ");
				
				if(curNode.nextLinks.get(i).endNode != lastNode && nextNodes.contains(curNode.nextLinks.get(i).endNode) == false)
					nextNodes.add(curNode.nextLinks.get(i).endNode);
			}
			s.append("\n");
			
			if(curNodes.isEmpty() && !nextNodes.isEmpty())
			{
				/*if(nextNodes.get(0).nextLinks.size() == 0)
				{
					System.err.println("MANYcn::toFullCNString : nextNodes contains a node with nextLinks.size() == 0");
					System.err.println("Node : "+nextNodes.get(0).toString());
					System.err.println("Node : "+lastNode.toString());
				}*/
				s.append("align ").append(alNum++).append(" ");
				ArrayList<Node> tmp = curNodes;
				curNodes = nextNodes;
				nextNodes = tmp;
			}
		}
		//System.err.println("********MANYcn::toFullCNString : END");
		return s.toString();
	}
	
	
	public boolean equals(MANYcn cn)
	{
		boolean error = false;
	
		if(nbMesh != cn.nbMesh)
		{
			System.err.println("orig.nbMesh="+nbMesh+" vs new.nbMesh"+cn.nbMesh+"  [BAD]");
			error = true;
		}
		
		if(cn.nodes.size() != nodes.size())
		{
			System.err.println("orig.nodes.size()="+nodes.size()+" vs new.nodes.size()"+cn.nodes.size()+"  [BAD]");
			error = true;
		}
		
		for(int i=0; i<nodes.size(); i++)
		{
			if(nodes.get(i).equals(cn.nodes.get(i)) == false)
			{
				error = true;
			}
		}
		return !error;
	}
	
	public MANYcn toCN()
	{
		MANYcn cn = new MANYcn();
		Node startNode = null, endNode = null;
		
		//handle first node here
		cn.firstNode = cn.createNode(firstNode.time);
		
		// then the other nodes ...
		for(int i=1; i<nodes.size(); i++)
		{
			Node n = nodes.get(i);
			endNode = cn.createNode(n.time);
			
			for(Link l : n.backLinks)
			{
				startNode = cn.timeToNode.get(l.startNode.time);
				if(startNode == null)
				{
					System.err.println("MANYcn::toCN : le Node n'existe pas encore ... pas normal");
					System.exit(0);
				}
				cn.createUniqueLink(startNode, endNode, l.probability, l.posterior, idToWord.get(l.wordId), l.sysids);
			}
		}
		cn.lastNode = endNode;
		cn.nbMesh = nbMesh;
		return cn;
	}
	
	/**
	 * 
	 */
	public static ArrayList<MANYcn> fullCNs2CNs(ArrayList<MANYcn> fullcns)
	{
		ArrayList<MANYcn> cns = new ArrayList<MANYcn>();
		
		for(MANYcn cn : fullcns)
		{
			cns.add(cn.toCN());
		}
		return cns;
	}
	
	/**
	 * @param args
	 */
	public static int main(String[] args)
	{
		
		String lst = args[0];
		ArrayList<String> files = MANYutilities.readStringList(lst);
		ArrayList<ArrayList<MANYcn>> theCNs = new ArrayList<ArrayList<MANYcn>>();
		
		for(String f : files)
		{
			ArrayList<MANYcn> l = MANYcn.loadFullCNs(f);
			theCNs.add(l);
			
			double[] w = new double[5];
			w[0] = 0.2;
			w[1] = 0.1;
			w[2] = 0.3;
			w[3] = 0.4;
			w[2] = 0.5;
			
			for(MANYcn cn : l)
			{
				//cn.setSysWeights(w);
			}
			
			
		}
			
		return 0;
	}	
}
