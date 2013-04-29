package org.lockss.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.*;

import org.lockss.db.JenaDbManager;
import org.mortbay.html.*;

import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;

public class Jena extends LockssServlet {

	private final String HEADING = "Enter a SPARQL query, then click \"Query\"";
	private final String NAME_QUERY_PARAMETER = "name";
	
	private JenaDbManager jenaDbManager = null;

	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		jenaDbManager = new JenaDbManager();
	}

	@Override
	protected void lockssHandleRequest() throws ServletException, IOException {
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
	    TextArea query = new TextArea(NAME_QUERY_PARAMETER, getParameter(NAME_QUERY_PARAMETER));
	    query.setSize(70, 3);
	    query.attribute("style", "border: 1px solid black; resize: none; padding: 5px;");
	    form.add(query);
	    
	    form.add("<br/>");
	    form.add("<input type=\"submit\" value=\"Query\" style=\"width: 512px;\"/>");
	    
	    form.add("</center>");
	    
	    return form;
	}
	
	private Element createResultsContainer() throws IOException {
		String queryString = String.valueOf(getParameter(NAME_QUERY_PARAMETER));
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		
		try {
            ResultSet results = jenaDbManager.query(queryString);
			ResultSetFormatter.outputAsJSON(output, results);
		} catch (Exception e) {
			output.write(e.getMessage().getBytes());
		}

		
		Composite comp = new Composite();
		comp.add("<center>");
		comp.add("<h2>Results</h2>");
		comp.add("<div style=\"width: 512px; height: 200px; overflow: scroll; border: 1px solid black; text-align: left; padding: 5px;\">");
		
		comp.add(output.toString());
		
		comp.add("</div></center>");
		
		return comp;
	}
}
