package com.bbn.mt.terp;

import java.util.concurrent.Callable;
import java.util.logging.Logger;


public class TERtask implements Callable<TERoutput>
{
	private String params = null;
	private String outfile = null;
	private TERplus terp = null;
	private int id = -1;
	
	private TERtask(){}
	public TERtask(int id, String params, String outfile)
	{
		this.id = id;
		this.params = params;
		this.outfile = outfile;
	}
	
	
	@Override
	public TERoutput call() throws Exception
	{
		terp = new TERplus();
		terp.logger = Logger.getLogger("Thread"+id);
		terp.setTerpParams(params);
		terp.allocate();
		TERoutput output = terp.run();
		if(output != null)
		{
			output.output_full_cns(outfile);
			output.output_cn(outfile+".merged");
		}
		terp = null;
		output.setParams(null);
		return output;
	}
}
