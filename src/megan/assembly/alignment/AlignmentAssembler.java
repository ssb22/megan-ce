/*
 *  Copyright (C) 2018 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package megan.assembly.alignment;

import jloda.graph.*;
import jloda.util.*;
import megan.alignment.gui.Alignment;
import megan.assembly.OverlapGraphViewer;
import megan.assembly.PathExtractor;
import megan.core.Director;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * assembles from an alignment
 * Daniel Huson, 5.2105
 */
public class AlignmentAssembler {
    private Graph overlapGraph;
    private Alignment alignment;
    private Node[][] paths;
    private NodeMap<String> node2readName;
    private ArrayList<Pair<String, String>> contigs;
    private List<Integer>[] readId2ContainedReads;

    /**
     * constructor
     */
    public AlignmentAssembler() {
    }

    /**
     * compute the overlap graph
     *
     * @param minOverlap
     * @param alignment
     * @param progress
     * @throws IOException
     * @throws CanceledException
     */
    public void computeOverlapGraph(int minOverlap, final Alignment alignment, ProgressListener progress) throws IOException, CanceledException {
        this.alignment = alignment;
        OverlapGraphBuilder overlapGraphBuilder = new OverlapGraphBuilder(minOverlap);
        overlapGraphBuilder.apply(alignment, progress);
        overlapGraph = overlapGraphBuilder.getOverlapGraph();
        node2readName = overlapGraphBuilder.getNode2ReadNameMap();
        readId2ContainedReads = overlapGraphBuilder.getReadId2ContainedReads();
    }

    /**
     * show the overlap graph
     *
     * @param dir
     * @param progress
     * @throws CanceledException
     */
    public void showOverlapGraph(Director dir, ProgressListener progress) throws CanceledException {
        final OverlapGraphViewer overlapGraphViewer = new OverlapGraphViewer(dir, overlapGraph, node2readName, paths);
        overlapGraphViewer.apply(progress);
    }

    /**
     * write the overlap graph
     *
     * @param writer
     * @return
     * @throws IOException
     * @throws CanceledException
     */
    public Pair<Integer, Integer> writeOverlapGraph(Writer writer) throws IOException, CanceledException {
        final NodeArray<String> names = new NodeArray<>(overlapGraph);
        final NodeArray<String> sequences = new NodeArray<>(overlapGraph);

        for (Node v = overlapGraph.getFirstNode(); v != null; v = v.getNext()) {
            int i = (Integer) v.getInfo();
            sequences.set(v, alignment.getLane(i).getBlock());
            names.set(v, Basic.getFirstWord(alignment.getLane(i).getName()));
        }
        final Map<String, NodeArray<?>> label2nodes = new TreeMap<>();
        label2nodes.put("label", names);
        label2nodes.put("sequence", sequences);

        final EdgeArray<Integer> overlap = new EdgeArray<>(overlapGraph);
        for (Edge e = overlapGraph.getFirstEdge(); e != null; e = e.getNext()) {
            overlap.set(e, (Integer) e.getInfo());
        }
        final Map<String, EdgeArray<?>> label2edges = new TreeMap<>();
        label2edges.put("label", null);
        label2edges.put("overlap", overlap);

        overlapGraph.writeGML(writer, "Overlap graph generated by MEGAN6", true, alignment.getName(), 1, label2nodes, label2edges);
        return new Pair<>(overlapGraph.getNumberOfNodes(), overlapGraph.getNumberOfEdges());
    }

    /**
     * compute contigs. Also sorts alignment by contigs
     *
     * @param alignmentNumber
     * @param minReads
     * @param minCoverage
     * @param minLength
     * @param progress
     * @return
     * @throws CanceledException
     */
    public int computeContigs(int alignmentNumber, int minReads, double minCoverage, int minLength, boolean sortAlignmentByContigs, ProgressListener progress) throws CanceledException {
        final PathExtractor pathExtractor = new PathExtractor(overlapGraph, readId2ContainedReads);
        pathExtractor.apply(progress);
        paths = pathExtractor.getPaths();

        final ContigBuilder contigBuilder = new ContigBuilder(pathExtractor.getPaths(), pathExtractor.getSingletons(), readId2ContainedReads);

        contigBuilder.apply(alignmentNumber, alignment, minReads, minCoverage, minLength, sortAlignmentByContigs, progress);
        contigs = contigBuilder.getContigs();

        return contigBuilder.getCountContigs();
    }

    public ArrayList<Pair<String, String>> getContigs() {
        return contigs;
    }

    /**
     * write contigs
     *
     * @param w
     * @param progress
     * @throws CanceledException
     * @throws IOException
     */
    public void writeContigs(Writer w, ProgressListener progress) throws CanceledException, IOException {
        progress.setSubtask("Writing contigs");
        progress.setMaximum(contigs.size());
        progress.setProgress(0);
        for (Pair<String, String> pair : contigs) {
            w.write(pair.getFirst().trim());
            w.write("\n");
            w.write(pair.getSecond().trim());
            w.write("\n");
            progress.incrementProgress();
        }
        w.flush();
        if (progress instanceof ProgressPercentage)
            ((ProgressPercentage) progress).reportTaskCompleted();
    }
}
