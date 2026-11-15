package practice.prep.problems;

import java.io.*;
import java.util.*;

/*

Populating "next" pointers in a binary tree
Question
Given a binary tree made up of nodes that look like so:

class Node
{
    Node left;
    Node right;
    Node next;
}
Populate the next pointer such that it points to the node immediately to the right of it, or null if there isn't one given the root of the tree. You cannot assume that next is initialized to null, ie it must be explicitly set. For example:

     1
   /   \
  2     3
 / \   / \
4   5 6   7
Becomes

     1 -> null
   /   \
  2  -> 3 -> null
 / \   / \
4 ->5->6 ->7 -> null

Ideal solution uses fixed storage

       1 -> null
    2 -> 3 -> null
        6 -> 7 -> null

 */

class Solution {
  public static void main(String[] args) {
    Node root = new Node("1");
    root.left = new Node("2");
    root.right = new Node("3");
    root.right.left = new Node("4");
    root.right.right = new Node("5");

    populateNextPointers(root);
    printList(root);
    printList(root.left);
    printList(root.right.left);
  }

  private static void printList(Node node) {
    while (node != null) {
      System.out.print(String.format("%s -> ", node.label));
      node = node.next;
    }

    System.out.println();
  }

  public static void populateNextPointers(Node root) {
    if (root == null) {
        return;
    }
    
    Node currList = root;
    root.next = null;
    do {
      Node nextList = buildNextList(currList);
      currList = nextList;
    } while (currList != null);
  }

  private static Node buildNextList(Node currList) {
    Node nextListHead = null;
    Node nextList = null;
    while (currList != null) {
      if (currList.left != null) {
        if (nextList != null) {
          nextList.next = currList.left;
          nextList = nextList.next;
        } else {
          nextList = currList.left;
          nextListHead = nextList;
        }

        currList.left.next = null;
      }

      if (currList.right != null) {
        if (nextList != null) {
          nextList.next = currList.right;
          nextList = nextList.next;
        } else {
          nextList = currList.right;
          nextListHead = nextList;
        }

        currList.right.next = null;
      }

      currList = currList.next;
    }

    return nextListHead;
  }

  public static class Node {
    public String label;
    public Node left;
    public Node right;
    public Node next;

    Node(String label) {
      this.label = label;
    }
  }
}
