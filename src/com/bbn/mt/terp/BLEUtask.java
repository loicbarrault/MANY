	package com.bbn.mt.terp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import edu.lium.decoder.MANYcn;

public class BLEUtask implements Callable<BLEUcounts>
{
	private boolean DEBUG = false;
	private int id = -1;
	private Logger logger = null;
	private TERoutput terpout = null;
	private Map<TERid, List<String[]>> all_refs_tok = null;

	private int max_ngram = 4;
	
	private BLEUtask() {}

	public BLEUtask(int id, TERoutput terpout, Map<TERid, List<String[]>> all_refs_tok)
	{
		this.id = id;
		this.terpout = terpout;
		this.all_refs_tok  = all_refs_tok;
	}

	@Override
	public BLEUcounts call() throws Exception
	{
		logger = Logger.getLogger("BLEUThread " + id);
		//System.err.println("Starting task " + id);
		logger.info("Starting task " + id);
		
		//int nbSentences = terpout.getResults().size();
		//double[] bleu_scores = new double[nbSentences];
		
		BLEUcounts bleu_counts = new BLEUcounts();
		
		int npid = 0;
		//for (int j = 0; j < nbSentences; j++) // for each sentences
		for (TERid pid : terpout.getIds())
		{
			if(npid % 100 == 0)
			{
				logger.info("Processing sentence #"+npid+" : "+pid);
				//System.err.println("BLEUtask::call : Processing sentence #"+npid+" : "+pid);
			}
			
			TERalignmentCN al = (TERalignmentCN) (terpout.getResult(pid));
			if(al == null)
			{
				logger.warning("No result for sentence #"+npid+" : "+pid+" ... skipping!");
				continue;
			}
			
			List<String[]> refs = all_refs_tok.get(pid);
			
			if (refs.size() > 1 && DEBUG)
			{
				for(int n=0; n<refs.size(); n++)
				{
					//System.out.println("\tref["+n+"] = "+TERutilities.join(" ", refs.get(n)));
					logger.info("\tref["+n+"] = "+TERutilities.join(" ", refs.get(n)));
				}
			}
	
			if ((refs.size() == 0) || ((refs.size() == 1) && (refs.get(0).length == 0)))
			{
				System.out.println("WARNING BLEUtask::call : Blank or empty reference for segment: " + pid + " ... skipping !");
				//bleu_scores[npid] = 0.0; // 0.0 is min BLEU
				npid++;
				continue;
			}
			else if(DEBUG)
			{
				for(int i=0; i<refs.size(); i++)
				{
					System.err.print("REF[" + i + "] = ");
					System.err.println(TERutilities.join(" ", refs.get(i)));
				}
			}
			
			MANYcn cn = new MANYcn(al.cn, al.cn_scores, null);
			//logger.info("MANYcn created  : ");
			//logger.info(cn.toCNString());
			BLEUcn bleu = new BLEUcn(id, max_ngram);
			bleu.calc(cn, refs);
			
			bleu_counts.translation_length += bleu.bc.translation_length;
			bleu_counts.closest_ref_length += bleu.bc.closest_ref_length;
			for(int n=0; n<max_ngram; n++)
			{
				bleu_counts.ngram_counts[n] += bleu.bc.ngram_counts[n];
				bleu_counts.ngram_counts_ref[n] += bleu.bc.ngram_counts_ref[n];
				bleu_counts.ngram_counts_clip[n] += bleu.bc.ngram_counts_clip[n];
			}
			npid++;	
		}
		return bleu_counts;
	}
}
