/*
 * Copyright 2019 Igor Maznitsa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.igormaznitsa.jlinktest;

import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Main {

  private static String expressionBreakWithValue(int switchArg) {
    return switch (switchArg) {
      case 1, 2:
        yield "one or two";
      case 3:
        yield "three";
      default:
        yield "smaller than one or bigger than three";
    };
  }

  public static void main(String... args) {
    System.out.println("Hello world : " + expressionBreakWithValue(3));

    try {
      File inputFile = new File("input.txt");
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(inputFile);
      doc.getDocumentElement().normalize();
      System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
      NodeList nList = doc.getElementsByTagName("student");
      System.out.println("----------------------------");

      for (int temp = 0; temp < nList.getLength(); temp++) {
        Node nNode = nList.item(temp);
        System.out.println("\nCurrent Element :" + nNode.getNodeName());

        if (nNode.getNodeType() == Node.ELEMENT_NODE) {
          Element eElement = (Element) nNode;
          System.out.println("Student roll no : "
              + eElement.getAttribute("rollno"));
          System.out.println("First Name : "
              + eElement
              .getElementsByTagName("firstname")
              .item(0)
              .getTextContent());
          System.out.println("Last Name : "
              + eElement
              .getElementsByTagName("lastname")
              .item(0)
              .getTextContent());
          System.out.println("Nick Name : "
              + eElement
              .getElementsByTagName("nickname")
              .item(0)
              .getTextContent());
          System.out.println("Marks : "
              + eElement
              .getElementsByTagName("marks")
              .item(0)
              .getTextContent());
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}