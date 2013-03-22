package edu.lium.debug;

import java.util.ArrayList;
import junit.framework.TestCase;
import com.bbn.mt.terp.BLEUcn;
import com.bbn.mt.terp.BLEUcounts;
import com.bbn.mt.terp.TERutilities;
import edu.lium.decoder.MANYcn;
import edu.lium.utilities.MANYutilities;

public class MANYtest extends TestCase
{
	public MANYtest(String name)
	{
		super(name);
	}

	public void testReadWrite()
	{
		String file = "output.many.cn.";
		
		for(int i=0; i<3; i++)
		{
			System.err.println("Loading "+file+i);
			ArrayList<MANYcn> cns = MANYcn.loadFullCNs(file+i);
			System.err.println("Reading "+file+i+" successfully !!");
			
			MANYcn.outputFullCNs(cns, "full."+file+i);
			System.err.println("Writing full."+file+i+" successfully !!");
			
			ArrayList<MANYcn> cns_bis = MANYcn.loadFullCNs("full."+file+i);
			System.err.println("Reading full."+file+i+" successfully !!");
			
			assertTrue(areEqual(cns, cns_bis));
			System.err.println("Loading "+file+i+" successfully !!");
		}
	}

	public void testChangeWeights()
	{
		String file = "output.many.cn.";
		
		for(int i=0; i<3; i++)
		{
			System.err.println("Loading "+file+i);
			ArrayList<MANYcn> cns = MANYcn.loadFullCNs(file+i);
			System.err.println("Reading "+file+i+" successfully !!");
			
			float[] sw = new float[3];
			sw[0] = 0.12f;
			sw[1] = 0.34f;
			sw[2] = 0.56f;
			MANYcn.changeSysWeights(cns, sw);
			
			
			MANYcn.outputFullCNs(cns, "full."+file+i);
			System.err.println("Writing full."+file+i+" successfully !!");
			
			MANYcn.outputCNs(MANYcn.fullCNs2CNs(cns), "new."+file+i);
			System.err.println("Writing new."+file+i+" successfully !!");
			
			ArrayList<MANYcn> cns_bis = MANYcn.loadFullCNs("full."+file+i);
			System.err.println("Reading full."+file+i+" successfully !!");
			
			assertTrue(areEqual(cns, cns_bis));
			System.err.println("Loading "+file+i+" successfully !!");
		}
	}
	
	private boolean areEqual(ArrayList<MANYcn> cns, ArrayList<MANYcn> cns2)
	{
		boolean error = false;
		System.err.println("--- Checking number of CNs : ");
		if(cns.size() != cns2.size())
		{
			System.err.println("orig.size()="+cns.size()+" vs new.size()="+cns2.size()+ " [BAD]");
			error = true;
		}
		else
		{
			System.err.println("orig.size()="+cns.size()+" vs new.size()="+cns2.size()+ " [OK]");
		}
		
		System.err.println("--- Checking every CN : ");
		for(int i=0; i<cns.size(); i++)
		{
			if(cns.get(i).equals(cns2.get(i)) == false)
			{
				System.err.println("orig.cn["+i+"] vs new.cn["+i+"]  [BAD]");
				error = true;
			}
			else
			{
				System.err.println("orig.cn["+i+"] vs new.cn["+i+"]  [OK]");
			}
		}
		return !error;
	}
	
	
	public void testBleuCN()
	{
		//System.err.println("Starting test BLEU ... ");
		
		String file = "output.many.cn.";
		
		String ref_file = "ref";
		//int nb_refs = 1; //nombre de ref pour chaque phrase
		
		//System.err.println("Loading ref file : "+ref_file);
		ArrayList<String[]> refs = MANYutilities.loadRefs(ref_file, 1);
		//System.err.println("Reading "+ref_file+" successfully !!");
		
		for(String[] r : refs)
		{
			System.err.println("REF ("+r.length+"): "+TERutilities.join(r, " "));
		}
		
		for(int i=0; i<3; i++)
		//for(int i=0; i<1; i++)
		{
			//System.err.println("Loading "+file+i);
			ArrayList<MANYcn> full_cns = MANYcn.loadFullCNs(file+i);
			//System.err.println("Read "+file+i+" successfully !!");
			ArrayList<MANYcn> cns = MANYcn.fullCNs2CNs(full_cns);
			//System.err.println("Converted full_cns to cns successfully !!");
			
			BLEUcounts bleu_counts = new BLEUcounts();
			
			for(int j=0; j<cns.size(); j++)
			//for(int j=0; j<1; j++)
			{
				MANYcn cn = cns.get(j);
				BLEUcn bleu = new BLEUcn(j);
				bleu.calc(cn, refs.get(j));
				
				bleu_counts.translation_length += bleu.bc.translation_length;
				bleu_counts.closest_ref_length += bleu.bc.closest_ref_length;
				//System.err.printf("len ratio=%.3f, hyp_len=%d, ref_len=%d\n", (float)bleu.translation_length / (float)bleu.closest_ref_length, bleu.translation_length, bleu.closest_ref_length);
				
				for(int n=0; n<BLEUcounts.max_ngram_size; n++)
				{
					bleu_counts.ngram_counts[n] += bleu.bc.ngram_counts[n];
					bleu_counts.ngram_counts_ref[n] += bleu.bc.ngram_counts_ref[n];
					bleu_counts.ngram_counts_clip[n] += bleu.bc.ngram_counts_clip[n];
				}
				
				//System.err.printf("len ratio=%.3f, hyp_len=%d, ref_len=%d\n",	(float)bleu_counts.length_translation / (float)bleu_counts.length_reference, bleu_counts.length_translation, bleu_counts.length_reference);
				//System.out.println("Sys["+i+"] Sent["+j+"] : "+b.);
				//System.err.println("Sys["+i+"] Sent["+j+"] : "+String.format("%.4f", bleu));
				//return;
			}
			double bleuScore = bleu_counts.computeBLEU();
			double recallScore = bleu_counts.computeRecall();
			System.err.println("bleu : " + bleuScore+" recall : "+recallScore);
			
		}
	}
	
	
}
