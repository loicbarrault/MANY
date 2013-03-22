package edu.lium.utilities;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import edu.cmu.sphinx.linguist.dictionary.Word;
import edu.lium.decoder.NGram;

public abstract class MANYutilities
{

	public static ArrayList<String> readStringList(String file)
	{
		ArrayList<String> list = new ArrayList<String>();
		String line = null;
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(file));
			while ((line = reader.readLine()) != null)
			{
				if (line.length() > 0)
				{
					list.add(line);
				}
			}
			reader.close();
		}
		catch (IOException ioe)
		{
			System.err.println("I/O error when reading file" + file + " " + ioe);
			ioe.printStackTrace();
		}
		
		return list;
	}

	public static ArrayList<String[]> loadRefs(String refFile, int nb_refs)
	{
		ArrayList<String[]> to_return = new ArrayList<String[]>();
		String[] refs = null;
		String line = null;
		int nb = 0;
		boolean showLines = false;
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(refFile));
			line = reader.readLine();
			while (line != null)
			{
				if(nb%nb_refs == 0)
				{
					refs = new String[nb_refs];
					to_return.add(refs);
					nb = 0;
				}
				
				if(showLines) System.err.println("LINE : "+line);
				if (line.length() > 0)
				{
					refs[nb++] = line;
				}
				else
				{
					if(showLines) System.err.println(" -> EMPTY !");
				}
				line = reader.readLine();
			}
		}
		catch(IOException ioe)
		{
			System.err.println("ERROR MANYutilities::loadRefs : I/O exception : ");
			ioe.printStackTrace();
			System.exit(-1);
		}
		return to_return;
	}

	public static String join(List<String> ws, String sep)
	{
		if(ws == null || ws.size() == 0 || sep == null)
			return null;
		
		StringBuilder to_return = new StringBuilder(ws.get(0));
		
		for(int i=1; i<ws.size(); i++)
		{
			to_return.append(sep).append(ws.get(i));
		}
		
		return to_return.toString();
	}

	public static List<String> getStringWordList(Word[] ws, int from, int to)
	{
		if(ws == null || ws.length == 0 )
			return null;
		
		if(from < 0 || to > ws.length || from > to || from > ws.length)
			return null;
		
		ArrayList<String> to_return = new ArrayList<String>();
		for(int i=from; i<=to; i++)
		{
			to_return.add(ws[i].getSpelling());
		}
		
		return to_return;
	}
	public static NGram getNGram(Word[] ws, int from, int to)
	{
		if(ws == null || ws.length == 0 )
			return null;
		
		if(from < 0 || to > ws.length || from > to || from > ws.length)
			return null;
		
		NGram to_return = new NGram();
		for(int i=from; i<=to; i++)
		{
			to_return.add(ws[i].getSpelling());
		}
		
		return to_return;
	}
}
