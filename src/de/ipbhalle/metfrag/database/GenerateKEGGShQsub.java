/*
*
* Copyright (C) 2009-2010 IPB Halle, Sebastian Wolf
*
* Contact: swolf@ipb-halle.de
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <http://www.gnu.org/licenses/>.
*
*/
package de.ipbhalle.metfrag.database;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class GenerateKEGGShQsub {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		
		String writePath = "";
		
		if(args[0] != null)
			writePath = args[0];
		else
		{
			System.err.println("Error no path given!");
			System.exit(1);
		}
		
		String path = "/vol/mirrors/kegg/mol/";
		//loop over all files in folder
		File f = new File(path);
		File files[] = f.listFiles();
		Arrays.sort(files);
		

		int count = 0;
		int globalCount = 0;
		String fileNames = "";
		for (int i = 0; i < files.length; i++) {
			
			String fileName = files[i].getName();
			int dotPos = fileName.indexOf(".");
			String extension = "";
			if(dotPos >= 0)
				extension = fileName.substring(dotPos);

			if(files[i].isFile() && extension != "" && extension.equals(".mol"))
			{
				count++;
				fileNames += fileName + "-";
			}
			
			if(count == 500 || i == (files.length - 1))
			{
				File f2 = new File(writePath + "KEGG_" + globalCount + ".sh"); 
				
				BufferedWriter out = new BufferedWriter(new FileWriter(f2));
				out.write("#!/bin/bash");
				out.newLine();
				out.write("#-s /bin/sh");
				out.newLine();
				out.write("#-q MSBI");
				out.newLine();
		  		out.write("java -Xms1500m -Xmx2500m -Dproperty.file.path=/home/swolf/src/MetFragNew/ -jar /home/swolf/sgeQsubScripts/KEGGToMassBankDB.jar \"" + fileNames + "\"");
			  	out.close();
			  	
			  	count = 0;
			  	fileNames = "";
			  	globalCount++;
			}
		}

	}
}
