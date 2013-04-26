package org.lockss.servlet;

import java.io.IOException;

import javax.servlet.*;

import org.mortbay.html.*;

public class Jena extends LockssServlet {
	
	private final String HEADING = "Enter a SPARQL query, then press enter";
	
	private final String NAME_QUERY_PARAMETER = "name";

	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	@Override
	protected void lockssHandleRequest() throws ServletException, IOException {
		String action = getParameter(NAME_QUERY_PARAMETER);
		displayPage();
	}
	
	private void displayPage() throws IOException {
		Page page = newPage();
		page.add(createHeading());
	    page.add(createQueryForm());
	    page.add(createResultsContainer());
		endPage(page);
	}
	
	private Element createHeading() {
		Composite comp = new Composite();
		
		comp.add("<center>");
		comp.add("<h2>" + HEADING +"</h2>");
		comp.add("</center>");
		
		return comp;
	}
	
	private Element createQueryForm() {
	    Form form = new Form(srvURL(myServletDescr()));
	    form.method("POST");
	    
	    form.add("<center>");
	    TextArea query = new TextArea(NAME_QUERY_PARAMETER);
	    query.setSize(70, 10);
	    query.attribute("style", "border: 1px solid black; resize: none;");
	    form.add(query);
	    
	    form.add("<br/>");
	    form.add("<input type=\"submit\" value=\"Query\" style=\"width: 512px;\"/>");
	    
	    form.add("</center>");
	    
	    return form;
	}
	
	private Element createResultsContainer() {
		Composite comp = new Composite();
		comp.add("<center>");
		comp.add("<h2>Results</h2>");
		comp.add("<div style=\"width: 512px; height: 136px; overflow: scroll; border: 1px solid black;\">");
		
		Table results = new Table();
		comp.add(results);
		
		comp.add("</div></center>");
		
		return comp;
	}
}
