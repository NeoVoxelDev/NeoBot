package dev.neovoxel.neobot.script;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.util.*;

@Data
@RequiredArgsConstructor
public class Script {
    private final int schemaVersion;
    private final String id;
    private final String name;
    private final String author;
    private final String version;
    private final List<String> loadbefore = new ArrayList<>();
    private final List<String> loadafter = new ArrayList<>();
    private final List<String> depends = new ArrayList<>();
    private String description;
    private final File entrypoint;

    public String toExpression() {
        return author + ":" + id + ":" + version;
    }

    public boolean checkDepends(Collection<Script> checkedScripts) {
        for (String dependency : depends) {
            boolean found = false;
            for (Script checkedScript : checkedScripts) {
                if (checkedScript.getId().equals(dependency)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    public static List<Script> sortScripts(Set<Script> scripts) {
        if (scripts == null || scripts.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Script> scriptMap = new HashMap<>();
        for (Script script : scripts) {
            scriptMap.put(script.getId(), script);
        }

        Map<String, Set<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (Script script : scripts) {
            String scriptName = script.getId();
            graph.putIfAbsent(scriptName, new HashSet<>());
            inDegree.putIfAbsent(scriptName, 0);
        }

        for (Script script : scripts) {
            String currentScript = script.getId();

            for (String dependency : script.getDepends()) {
                if (scriptMap.containsKey(dependency)) {
                    graph.get(dependency).add(currentScript);
                }
            }

            for (String afterDependency : script.getLoadafter()) {
                if (scriptMap.containsKey(afterDependency)) {
                    graph.get(afterDependency).add(currentScript);
                }
            }

            for (String beforeDependency : script.getLoadbefore()) {
                if (scriptMap.containsKey(beforeDependency)) {
                    graph.get(currentScript).add(beforeDependency);
                }
            }
        }

        for (Set<String> dependencies : graph.values()) {
            for (String dependent : dependencies) {
                inDegree.put(dependent, inDegree.get(dependent) + 1);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<String> sortedNames = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sortedNames.add(current);

            for (String neighbor : graph.get(current)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        if (sortedNames.size() != scripts.size()) {
            throw new IllegalStateException("Failed to sort");
        }

        List<Script> sortedScripts = new ArrayList<>();
        for (String name : sortedNames) {
            sortedScripts.add(scriptMap.get(name));
        }

        return sortedScripts;
    }
}
