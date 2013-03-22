package com.bbn.mt.terp;

import java.util.concurrent.Callable;
import java.util.logging.Logger;


public class TERcomputetask implements Callable<double[]>
{
	private String params = null;
	private TERplus terp = null;
	private int id = -1;
	private TERoutput output;
	
	private TERcomputetask(){}
	public TERcomputetask(int id, String params, TERoutput cns)
	{
		this.id = id;
		this.params = params;
		this.output = cns;
	}
	
	
	@Override
	public double[] call() throws Exception
	{
		System.err.println("Starting task "+id);
		terp = new TERplus();
		terp.logger = Logger.getLogger("RefThread"+id);
		terp.setTerpParams(params);
		terp.allocate();
		
		double[] ter_scores = terp.compute_ter(output);
		
		terp.deallocate();
		terp = null;
		return ter_scores;
	}

}
