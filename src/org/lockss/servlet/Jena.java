package org.lockss.servlet;

import java.io.IOException;

import javax.servlet.*;

import org.mortbay.html.*;

public class Jena extends LockssServlet {
	
	private final String HEADING = "Enter a SPARQL query and view the results.";
	
	private final String NAME_QUERY_PARAMETER = "name";

	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	@Override
	protected void lockssHandleRequest() throws ServletException, IOException {
		String action = getParameter(NAME_QUERY_PARAMETER);
		displayPage();
	}
	
	private void displayPage() throws IOException{
		Page page = newPage();
	    ServletUtil.layoutExplanationBlock(page, HEADING);
	    layoutErrorBlock(page);
	    page.add(createQueryForm());
		endPage(page);
	}
	
	private Element createQueryForm(){
	    Form form = new Form(srvURL(myServletDescr()));
	    form.method("POST");
	    
	    form.add("<center>");
	    TextArea query = new TextArea(NAME_QUERY_PARAMETER);
	    query.setSize(70, 10);
	    form.add(query);
	    form.add("</center>");
	    
	    return form;
	}
	
	private Element createResultsContainer(){
		return null;
	}
}
