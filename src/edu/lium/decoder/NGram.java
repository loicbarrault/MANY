package edu.lium.decoder;

import java.util.ArrayList;
import java.util.List;

public class NGram extends ArrayList<String>
{
	public NGram()
	{
		
	}
	
	public NGram(String ws)
	{
		super();
		add(ws);
	}
	
	public NGram(List<String> l)
	{
		addAll(l);
	}
	
	public NGram(NGram ngram, String ws)
	{
		super();
		addAll(ngram);
		add(ws);
	}

	public int getOrder()
	{
		return size()-1;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if(o instanceof NGram == false)
			return false;
		
		NGram n = (NGram) o;
		
		if(size() != n.size())
			return false;
		
		for(int i=0; i<size(); i++)
			if(get(i).equals(n.get(i)) == false)
				return false;
		
		return true;
	}
	
}
