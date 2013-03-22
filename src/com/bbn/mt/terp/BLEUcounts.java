package com.bbn.mt.terp;


public class BLEUcounts
{
	public static int max_ngram_size = 4;
	public int translation_length = 0;
	public int closest_ref_length = 0;
	public float[] ngram_counts = null;
	public float[] ngram_counts_clip = null;
	public float[] ngram_counts_ref = null;

	public BLEUcounts()
	{
		ngram_counts = new float[BLEUcounts.max_ngram_size];
		ngram_counts_ref = new float[BLEUcounts.max_ngram_size];
		ngram_counts_clip = new float[BLEUcounts.max_ngram_size];
	}

	public double computeBLEU()
	{
		System.err.println(" ------- BLEU PREC ");
		
		double brevity = 1;
		if (translation_length < closest_ref_length)
		{
			brevity = Math.exp(1 - (double)closest_ref_length / (double)translation_length);
		}
		//System.err.println("brevity = "+brevity);

		double bleus[] = new double[BLEUcounts.max_ngram_size];
		double log_sum = 0;
		for (int n = 0; n < BLEUcounts.max_ngram_size; n++)
		{
			System.err.print("CLIP[" + n + "]:" + ngram_counts_clip[n] + " TOTAL[" + n + "]:" + ngram_counts[n]);
			if (ngram_counts[n] != 0)
			{
				bleus[n] = ngram_counts_clip[n] / ngram_counts[n];
			}
			else
			{
				bleus[n] = 0;
			}
			double l = myLog10(bleus[n]);
			System.err.println(" LOG[" + n + "] = " + l);
			log_sum += l;
		}

		double bleu = brevity * Math.exp(log_sum * 0.25);
		/*System.err.printf("BLEU_PREC = %.2f, %.1f/%.1f/%.1f/%.1f (BP=%.3f, ratio=%.3f, hyp_len=%d, ref_len=%d)\n",
				100 * bleu, 100 * bleus[0], 100 * bleus[1], 100 * bleus[2], 100 * bleus[3], brevity,
				(float) translation_length / (float) closest_ref_length, translation_length, closest_ref_length);
		 */
		return bleu;
	}

	public double computeRecall()
	{
		System.err.println(" ------- BLEU RECALL ");
		
		double brevity = 1;
		if (translation_length < closest_ref_length)
		{
			brevity = Math.exp(1 - (double)closest_ref_length / (double)translation_length);
		}

		double bleus[] = new double[BLEUcounts.max_ngram_size];
		double log_sum = 0;
		for (int n = 0; n < BLEUcounts.max_ngram_size; n++)
		{
			System.err.print("CLIP[" + n + "]:" + ngram_counts_clip[n] + " REF[" + n + "]:"
					+ ngram_counts_ref[n]);
			if (ngram_counts[n] != 0)
			{
				bleus[n] = ngram_counts_clip[n] / ngram_counts_ref[n];
			}
			else
			{
				bleus[n] = 0;
			}
			double l = myLog10(bleus[n]);
			System.err.println(" LOG[" + n + "] = " + l);
			log_sum += l;
		}

		// System.err.println("log_sum = "+log_sum);
		double bleu = brevity * Math.exp(log_sum * 0.25);
		/*System.err.printf(
				"BLEU_RECALL = %.2f, %.1f/%.1f/%.1f/%.1f (BP=%.3f, ratio=%.3f, hyp_len=%d, ref_len=%d)\n",
				100 * bleu, 100 * bleus[0], 100 * bleus[1], 100 * bleus[2], 100 * bleus[3], brevity,
				(float) translation_length / (float) closest_ref_length, translation_length, closest_ref_length);
		 */
		return bleu;
	}

	private double myLog10(double d)
	{
		if (d == 0)
			// return -Double.MAX_VALUE;
			return -99999;
		// System.err.println("d != -1 : "+d);
		return Math.log10(d);
	}
}