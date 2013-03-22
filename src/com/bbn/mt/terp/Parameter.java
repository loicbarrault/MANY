package com.bbn.mt.terp;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.bbn.mt.terp.TERpara.OPTIONS;
public class Parameter
{
	private static Pattern opts_p = Pattern.compile("^\\s*-(\\S+)\\s*$");
	private static Pattern para_pat = Pattern.compile("^([^:]+):(.*)$");
	public static enum OPT_TYPES
	{
		FILENAME, STRING, INTEGER, DOUBLE, BOOLEAN, DOUBLELIST, INTLIST, STRINGLIST, PARAMFILE, SHOWUSAGE, FLOATLIST
	}
	private HashMap<Object, Object> paras = new HashMap<Object, Object>();
	private HashMap<Object, String> param_names = new HashMap<Object, String>();
	private HashMap<String, Object> revName = new HashMap<String, Object>();
	private HashMap<Object, OPT_TYPES> opt_type = new HashMap<Object, OPT_TYPES>();
	private ArrayList<Object> options = new ArrayList<Object>();
	private HashMap<Object, Object[]> command_flags = new HashMap<Object, Object[]>();
	private HashMap<Object, Object> command_arg_flags = new HashMap<Object, Object>();
	private String usage_statement = "";
	private boolean usage_quit = true;
	
	public Parameter()
	{
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.VERBOSE, "Verbose (boolean)", new Boolean(false));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.NORMALIZE, "Normalize (boolean)", new Boolean(false));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.NEWNORM, "New Normalization (boolean)", new Boolean(false));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.USE_AVE_LEN, "Use Average Length (boolean)", new Boolean(
				true));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.SHOW_ALL_REFS, "Show All References (boolean)", new Boolean(
				false));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.CASEON, "Case Sensitive (boolean)", new Boolean(false));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.NOPUNCTUATION, "Strip Punctuation (boolean)", new Boolean(
				false));
		add_opt(Parameter.OPT_TYPES.FILENAME, OPTIONS.REF, "Reference File (filename)", "");
		add_opt(Parameter.OPT_TYPES.FILENAME, OPTIONS.REF_SCORES, "Reference Scores File (filename)", "");
		add_opt(Parameter.OPT_TYPES.STRINGLIST, OPTIONS.HYP, "Hypothesis Files (list)", new String[0]);
		add_opt(Parameter.OPT_TYPES.STRINGLIST, OPTIONS.HYP_SCORES, "Hypothesis Scores Files (list)",
				new String[0]);
		add_opt(Parameter.OPT_TYPES.STRINGLIST, OPTIONS.FORMATS, "Output Formats (list)", Parameter
				.parse_stringlist("all"));
		add_opt(Parameter.OPT_TYPES.FILENAME, OPTIONS.OUTPFX, "Output Prefix (filename)", "");
		add_opt(Parameter.OPT_TYPES.INTEGER, OPTIONS.BEAMWIDTH, "Beam Width (integer)", new Integer(20));
		add_opt(Parameter.OPT_TYPES.FILENAME, OPTIONS.REFLEN, "Reference Length File (filename)", "");
		add_opt(Parameter.OPT_TYPES.INTEGER, OPTIONS.SHIFTDIST, "Shift Distance (integer)", new Integer(50));
		add_opt(Parameter.OPT_TYPES.INTEGER, OPTIONS.SHIFTSIZE, "Shift Size (integer)", new Integer(10));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.CAP_TER, "Cap Maximum TER (boolean)", new Boolean(false));
		// add_opt(Parameter.OPT_TYPES.FILENAME, OPTIONS.PHRASE_TABLE,
		// "Phrase Table (filename)", "");
		add_opt(Parameter.OPT_TYPES.STRING, OPTIONS.ADJUST_PHRASETABLE_FUNC, "Adjust Phrase Table Func (string)",
				"");
		add_opt(Parameter.OPT_TYPES.DOUBLELIST, OPTIONS.ADJUST_PHRASETABLE_PARAMS,
				"Adjust Phrase Table Params (float list)", new double[0]);
		add_opt(Parameter.OPT_TYPES.DOUBLE, OPTIONS.ADJUST_PHRASETABLE_MIN, "Adjust Phrase Table Min (float)",
				Double.NEGATIVE_INFINITY);
		add_opt(Parameter.OPT_TYPES.DOUBLE, OPTIONS.ADJUST_PHRASETABLE_MAX, "Adjust Phrase Table Max (float)",
				Double.POSITIVE_INFINITY);
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.USE_PORTER, "Use Porter Stemming (boolean)", new Boolean(
				false));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.USE_WORDNET, "Use WordNet Synonymy (boolean)", new Boolean(
				false));
		add_opt(Parameter.OPT_TYPES.FILENAME, OPTIONS.WORDNET_DB_DIR, "WordNet Database Directory (filename)",
						"");
		// add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.MULTI_REF,
		// "Merge References (boolean)", new Boolean(false));
		add_opt(Parameter.OPT_TYPES.FILENAME, OPTIONS.WORD_CLASS_FNAME, "Word Class File (filename)", "");
		add_opt(Parameter.OPT_TYPES.DOUBLE, OPTIONS.DELETE_COST, "Default Deletion Cost (float)", new Double(
						1.0));
		add_opt(Parameter.OPT_TYPES.DOUBLE, OPTIONS.STEM_COST, "Default Stem Cost (float)", new Double(1.0));
		add_opt(Parameter.OPT_TYPES.DOUBLE, OPTIONS.SYN_COST, "Default Synonym Cost (float)", new Double(1.0));
		add_opt(Parameter.OPT_TYPES.DOUBLE, OPTIONS.INSERT_COST, "Default Insertion Cost (float)",
				new Double(1.0));
		add_opt(Parameter.OPT_TYPES.DOUBLE, OPTIONS.SUBSTITUTE_COST, "Default Substitution Cost (float)",
				new Double(1.0));
		add_opt(Parameter.OPT_TYPES.DOUBLE, OPTIONS.MATCH_COST, "Default Match Cost (float)", new Double(0.0));
		add_opt(Parameter.OPT_TYPES.DOUBLE, OPTIONS.SHIFT_COST, "Default Shift Cost (float)", new Double(1.0));
		add_opt(Parameter.OPT_TYPES.PARAMFILE, OPTIONS.PARAM_FILE, "", "");
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.FILTER_PHRASE_TABLE, "Filter Phrase Table (boolean)",
				new Boolean(true));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.DUMP_PHRASETABLE, "Dump Phrase Table (boolean)",
				new Boolean(false));
		add_opt(Parameter.OPT_TYPES.STRING, OPTIONS.SHIFT_CONSTRAINT, "Shift Constraint (string)", "exact");
		add_opt(Parameter.OPT_TYPES.STRING, OPTIONS.SHIFT_STOP_LIST, "Shift Stop Word List (string)", "");
		add_opt(Parameter.OPT_TYPES.FILENAME, OPTIONS.WEIGHT_FILE, "Weight File (filename)", "");
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.GENERALIZE_NUMBERS, "Generalize Numbers (boolean)",
				new Boolean(false));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.NORM_NUMS, "Normalize Numbers (boolean)", new Boolean(
						false));
		add_opt(Parameter.OPT_TYPES.FILENAME, OPTIONS.NORM_FILE, "Normalization File (filename)", "");
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.NORM_PHRASE_TABLE, "Normalize Phrase Table (boolean)",
				new Boolean(false));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.SUM_DUP_PHRASES, "Sum Duplicate Phrases (boolean)",
				new Boolean(false));
		add_opt(Parameter.OPT_TYPES.FILENAME, OPTIONS.PHRASE_DB, "Phrase Database (filename)", "");
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.IGNORE_MISSING_HYP, "Ignore Missing Hypothesis (boolean)",
				new Boolean(false));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.IGNORE_SETID, "Ignore Test Set ID (boolean)", new Boolean(
				false));

		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.TRY_ALL_SHIFTS, "Try All Shifts (boolean)", new Boolean(
				false));
		add_opt(Parameter.OPT_TYPES.BOOLEAN, OPTIONS.CREATE_CONFUSION_NETWORK,
				"Create confusion Network (boolean)", new Boolean(false));
		add_opt(Parameter.OPT_TYPES.INTEGER, OPTIONS.REF_IDX,
				"Reference Index (integer)", new Integer(0));
		add_opt(Parameter.OPT_TYPES.DOUBLELIST, OPTIONS.SYS_WEIGHTS,
				"Systems weights (double list)", new double[0]);
		add_opt(Parameter.OPT_TYPES.INTLIST, OPTIONS.HYP_IDX,
				"Hypotheses Indexes (integer list)", new int[0]);
		
		// Set command-line flags
		addCmdBoolFlag(OPTIONS.NORMALIZE, 'N', "true");
		addCmdBoolFlag(OPTIONS.CASEON, 's', "true");
		addCmdBoolFlag(OPTIONS.CAP_TER, 'c', "true");
		addCmdBoolFlag(OPTIONS.USE_PORTER, 't', "true");
		addCmdBoolFlag(OPTIONS.VERBOSE, 'v', "true");
		addCmdBoolFlag(OPTIONS.IGNORE_MISSING_HYP, 'm', "true");
		addCmdBoolFlag(OPTIONS.CREATE_CONFUSION_NETWORK, 'C', "true");
		addCmdBoolFlag(OPTIONS.TRY_ALL_SHIFTS, 'A', "true");

		addCmdArgFlag(OPTIONS.REF, 'r');
		addCmdArgFlag(OPTIONS.HYP, 'h');
		addCmdArgFlag(OPTIONS.WORD_CLASS_FNAME, 'w');
		addCmdArgFlag(OPTIONS.WEIGHT_FILE, 'W');
		addCmdArgFlag(OPTIONS.FORMATS, 'o');
		addCmdArgFlag(OPTIONS.OUTPFX, 'n');
		addCmdArgFlag(OPTIONS.BEAMWIDTH, 'b');
		addCmdArgFlag(OPTIONS.REFLEN, 'a');
		addCmdArgFlag(OPTIONS.PARAM_FILE, 'p');
		addCmdArgFlag(OPTIONS.WORDNET_DB_DIR, 'd');
		addCmdArgFlag(OPTIONS.SHIFT_STOP_LIST, 'S');
		addCmdArgFlag(OPTIONS.PHRASE_DB, 'P');

		String[] usage_ar =
		{
				"java -jar terp.jar [-p parameter_file] -r <ref_file> -h <hyp_file> [-NstcvmC] ",
				" [-a alter_ref] [-b beam_width] [-o out_format -n out_pefix] [-w word_cost_file]",
				" [-P phrase_table_db] [-W weight_file] [-d WordNet_dict_dir] [-S shift_stopword_list]",
				" [ parameter_file1 parameter_file2 ... ]",
				" ---------------------------------------------------------------------------------",
				"  -r <ref-file> (required field if not specified in parameter file)",
				"    reference file in either TRANS or SGML format",
				"  -h <hyp-file> (required field if not specified in parameter file)",
				"    hypothesis file to score in either TRANS or SGML format",
				"  -p <parameter_file>",
				"    specifies parameters.  Command line arguments after the -p will override values",
				"    in the parameter file",
				"    Command line arguments before the -p will be overriden by values in parameter file",
				"    parameter file for this run can be output by specifying 'param' as an output format",
				"    many parameters can only be set using a parameter",
				"    Any additional arguments to TERp will be treated as parameter files and evaluated",
				"    after other command line arguments.",
				"  -N",
				"    Normalize and Tokenize ref and hyp",
				"  -s",
				"    use case sensitivity",
				"  -c",
				"    cap ter at 100%",
				"  -t",
				"    use porter stemmer to determine shift equivilence",
				"  -v",
				"    use verbose output when running",
				"  -m",
				"    ignore missing hypothesis segments (useful when doing parallelization)",
				"  -C",
				"    align all hypotheses to the reference and create a confusion network",
				"  -A",
				"    explore all shifts which do not decrease score (instead of only the best shift)",
				"  -a <alter-ref>",
				"    reference file, in either TRANS or SGML format, to use for calculating number of words in reference",
				"  -b <beam-width>", "    beam width to use for min edit distance calculations", "  -o <out_format>",
				"    set output formats:  all,sum,pra,xml,ter,sum_nbest,nist,html,param,weights,counts",
				"  -n <out_prefix", "    set prefix for output files", "  -P <phrase_table_db>",
				"    directory that contains phrase table database", "  -W <weight_file>",
				"    file that contains edit weights.", "  -d <WordNet_dictionary_dir>",
				"    set the path to the WordNet Dictionary Directory (of the form /opt/WordNet-3.0/dict/)",
				"  -S <shift-stop-word-list>",
				"    specify a file that contains a list of words that cannot be shifted without a non-stop word",
				"\nUsage for phrase table adjustment functions:", PhraseTable.valid_adjust_funcs(),};
		set_usage_statement(TERutilities.join("\n", usage_ar), true);
	}
	
	
	public void set_usage_statement(String s)
	{
		usage_statement = s;
	}
	public void set_usage_statement(String s, boolean quit_on_usage)
	{
		usage_statement = s;
		usage_quit = quit_on_usage;
	}
	public void addCmdBoolFlag(Object opt, char flag, String flagval)
	{
		if (opt_type.containsKey(opt))
		{
			if (command_flags.containsKey(flag)
					|| command_arg_flags.containsKey(flag))
			{
				System.err.println("WARNING: Flag '" + flag
						+ "' already registered");
			}
			else
			{
				Object[] ar = new Object[2];
				ar[0] = opt;
				ar[1] = flagval;
				command_flags.put(flag, ar);
			}
		}
		else
		{
			System.err
					.println("WARNING:  Cannot add command line flag for unregister option: "
							+ opt);
		}
	}
	public void addCmdArgFlag(Object opt, char flag)
	{
		if (opt_type.containsKey(opt))
		{
			if (command_flags.containsKey(flag)
					|| command_arg_flags.containsKey(flag))
			{
				System.err.println("WARNING: Flag '" + flag
						+ "' already registered");
			}
			else
			{
				command_arg_flags.put(flag, opt);
			}
		}
		else
		{
			System.err
					.println("WARNING:  Cannot add command line flag for unregister option: "
							+ opt);
		}
	}
	public void add_opt(OPT_TYPES ot, Object op, String name, Object initval)
	{
		if (!opt_type.containsKey(op))
		{
			options.add(op);
			param_names.put(op, name);
			paras.put(op, initval);
			revName.put(name, op);
			opt_type.put(op, ot);
		}
		else
		{
			System.err.println("WARNING: option " + op + " already defined");
		}
	}
	public void usage()
	{
		usage(null);
	}
	public void usage(String message)
	{
		if ((message != null) && (message.length() > 0))
			System.err.println(message);
		if ((this.usage_statement != null)
				&& (this.usage_statement.length() > 0))
			System.out.println(this.usage_statement);
		if (usage_quit)
		{
			if ((message == null) || (message.length() == 0))
				System.exit(0);
			else
				System.exit(-1);
		}
	}
	public String[] parse_args(String[] args, boolean allow_other_args)
	{
		ArrayList<String> excess = null;
		if (allow_other_args)
			excess = new ArrayList<String>();
		for (int i = 0; i < args.length; ++i)
		{
			Matcher m = opts_p.matcher(args[i]);
			if (m.matches())
			{
				String flags = m.group(1);
				boolean need_arg = false;
				for (int j = 0; j < flags.length(); j++)
				{
					char optfl = flags.charAt(j);
					if (command_flags.containsKey(optfl))
					{
						Object[] ar = (Object[]) command_flags.get(optfl);
						process_op_input(ar[0], (String) ar[1]);
					}
					else if (command_arg_flags.containsKey(optfl))
					{
						Object opt = command_arg_flags.get(optfl);
						if (need_arg)
						{
							usage("Multiple flags requiring arguments used together: "
									+ args[i]);
						}
						else if (i == (args.length - 1))
						{
							usage("Argument required when using flag: " + optfl);
						}
						else
						{
							process_op_input(opt, args[++i]);
							need_arg = true;
						}
					}
					else
					{
						usage("Invalid parameter flag: " + optfl);
					}
				}
			}
			else
			{
				if (allow_other_args)
				{
					excess.add(args[i]);
				}
				else
				{
					usage("Unknown argument " + args[i]);
				}
			}
		}
		if (excess == null)
			return null;
		return (String[]) 
			excess.toArray(new String[0]);
	}
	public void readPara(String fname)
	{
		try
		{
			BufferedReader fh = new BufferedReader(new FileReader(fname));
			String line;
			while ((line = fh.readLine()) != null)
			{
				Matcher mat = para_pat.matcher(line);
				if (mat.matches())
				{
					String field = mat.group(1);
					String val = mat.group(2);
					field = field.trim();
					val = val.trim();
					Object op = revName.get(field);
					if (op != null)
					{
						process_op_input(op, val);
					}
				}
			}
			fh.close();
		}
		catch (IOException ioe)
		{
			//System.out.println(ioe);
			ioe.printStackTrace();
			return;
		}
		return;
	}
	public String outputPara()
	{
		return this.toString();
	}
	public String toString()
	{
		// String tr = "";
		int max_len = 0;
		for (Object op : options)
		{
			String m = (String) param_names.get(op);
			if ((m == null) || (m.length() == 0))
				continue;
			if (max_len < m.length())
				max_len = m.length();
		}
		String outformat = "%-" + max_len + "s  : %s\n";
		Formatter fr = new Formatter();
		for (Object op : options)
		{
			String s = null;
			String name = (String) param_names.get(op);
			if ((name == null) || (name.length() == 0))
				continue;
			Object o = paras.get(op);
			OPT_TYPES otype = (OPT_TYPES) opt_type.get(op);
			if (o == null)
			{
				s = "";
			}
			else if (otype == OPT_TYPES.DOUBLELIST)
			{
				s = doublelist_toString((double[]) o);
			}
			else if (otype == OPT_TYPES.STRINGLIST)
			{
				s = stringlist_toString((String[]) o);
			}
			else if (otype == OPT_TYPES.INTLIST)
			{
				s = integerlist_toString((int[]) o);
			}
			else
			{
				s = o.toString();
			}
			fr.format(outformat, (String) param_names.get(op), s);
		}
		return fr.toString();
	}
	public void process_op_input(Object opt, String val)
	{
		//System.err.println("opt="+opt+" val="+val);
		Object oval = null;
		switch ((OPT_TYPES) opt_type.get(opt))
		{
			case SHOWUSAGE :
				usage();
				return;
			case PARAMFILE :
				if ((val == null) || val.equals(""))
					return;
				System.out.println("Loading parameters from " + val);
				readPara(val);
				return;
			case BOOLEAN :
				if (val.equals(""))
					return;
				oval = Boolean.valueOf(val);
				break;
			case STRING :
			case FILENAME :
				oval = val;
				break;
			case DOUBLE :
				if (val.equals(""))
					return;
				oval = Double.valueOf(val);
				break;
			case INTEGER :
				if (val.equals(""))
					return;
				oval = Integer.valueOf(val);
				break;
			case DOUBLELIST :
				if (val.equals(""))
					return;
				oval = parse_doublelist(val);
				break;
			case STRINGLIST :
				if (val.equals(""))
					return;
				oval = parse_stringlist(val);
				break;
			case INTLIST :
				if (val.equals(""))
					return;
				oval = parse_integerlist(val);
				break;
			default :
				System.err
						.println("ERROR.  Can't figure out how to load value for "
								+ opt + " with value " + val);
				System.exit(-1);
				return;
		}
		paras.put(opt, oval);
	}
	public static String doublelist_toString(double[] d)
	{
		String tr = "";
		for (int i = 0; i < d.length; i++)
		{
			if (i == 0)
				tr += d[i];
			else
				tr += " " + d[i];
		}
		return tr;
	}
	public static String integerlist_toString(int[] d)
	{
		String tr = "";
		for (int i = 0; i < d.length; i++)
		{
			if (i == 0)
				tr += d[i];
			else
				tr += " " + d[i];
		}
		return tr;
	}
	public static String stringlist_toString(String[] d)
	{
		String tr = "";
		for (int i = 0; i < d.length; i++)
		{
			if (i == 0)
				tr += d[i];
			else
				tr += " " + d[i];
		}
		return tr;
	}
	public static double[] parse_doublelist(String lst)
	{
		lst = lst.trim();
		String[] vals = lst.split("[, \\t]+");
		double[] tr = new double[vals.length];
		for (int i = 0; i < tr.length; i++)
		{
			tr[i] = Double.parseDouble(vals[i]);
		}
		return tr;
	}
	public static int[] parse_integerlist(String lst)
	{
		lst = lst.trim();
		String[] vals = lst.split("[, \\t]+");
		int[] tr = new int[vals.length];
		for (int i = 0; i < tr.length; i++)
		{
			tr[i] = Integer.parseInt(vals[i]);
		}
		return tr;
	}
	public static String[] parse_stringlist(String lst)
	{
		lst = lst.trim();
		String[] vals = lst.split("[, \\t]+");
		return vals;
	}
	public void set(Object opt, Object val)
	{
		paras.put(opt, val);
	}
	public Object get(Object o)
	{
		return paras.get(o);
	}
	public String get_string(Object o)
	{
		return (String) paras.get(o);
	}
	public boolean get_boolean(Object o)
	{
		return (Boolean) paras.get(o);
	}
	public double get_double(Object o)
	{
		return (Double) paras.get(o);
	}
	public int get_integer(Object o)
	{
		return (Integer) paras.get(o);
	}
	public double[] get_doublelist(Object o)
	{
		double[] d = (double[]) paras.get(o);
		if (d == null)
			return null;
		double[] tr = new double[d.length];
		for (int k = 0; k < d.length; k++)
			tr[k] = d[k];
		return tr;
	}
	public int[] get_integerlist(Object o)
	{
		int[] d = (int[]) paras.get(o);
		if (d == null)
			return null;
		int[] tr = new int[d.length];
		for (int k = 0; k < d.length; k++)
			tr[k] = d[k];
		return tr;
	}
	public String[] get_stringlist(Object o)
	{
		String[] d = (String[]) paras.get(o);
		if (d == null)
			return null;
		String[] tr = new String[d.length];
		for (int k = 0; k < d.length; k++)
			tr[k] = d[k];
		return tr;
	}
}
