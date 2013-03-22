package edu.lium.decoder;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import javax.management.InstanceNotFoundException;

public class DecodeTask implements Callable<ArrayList<String>>
{
	ArrayList<ArrayList<MANYcn>> all_cns = null;
	private float[] priors = null;
	private int id = -1;
	private Logger logger = null;
	private int from;
	private int to;
	private TokenPassDecoder decoder = null;
	ArrayList<String> outputs = null;
	
	public DecodeTask(int id, ArrayList<ArrayList<MANYcn>> cns, float[] priors, int from, int to, TokenPassDecoder decoder)
	{
		this.id = id;
		this.all_cns = cns;
		this.priors = priors;
		this.from = from;
		this.to = to;
		this.decoder  = decoder;
		outputs = new ArrayList<String>(to-from);
	}
	
	
	@Override
	public ArrayList<String> call() throws Exception
	{
		outputs = null;
		logger = Logger.getLogger("DecodeThread " + id);
		
		if(decoder == null)
			throw new InstanceNotFoundException("decoder has not been initialized");
		
		if(all_cns == null || this.all_cns.isEmpty())
			throw new InstanceNotFoundException("all_cns has not been initialized or is empty");
		
		for (int i = from; i < to; i++) // foreach considered sentences
		{
			//logger.info("run : Creating graph for sentence "+i	 );
			Graph g = new Graph(all_cns.get(i), priors);
			//g.printHTK("graph.htk_"+i+".txt");

			// Then we can decode this graph ...
			logger.info("run : decoding graph for sentence "+i);
			outputs.add(decoder.decode(i, g));
		}
		return outputs;
	}
}
