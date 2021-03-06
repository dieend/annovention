/*
 * Copyright 2010 Impetus Infotech.
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
package com.impetus.annovention;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.FieldInfo;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.annotation.Annotation;

import com.impetus.annovention.listener.ClassAnnotationDiscoveryListener;
import com.impetus.annovention.listener.FieldAnnotationDiscoveryListener;
import com.impetus.annovention.listener.MethodAnnotationDiscoveryListener;
import com.impetus.annovention.resource.ClassFileIterator;
import com.impetus.annovention.resource.JarFileIterator;
import com.impetus.annovention.resource.ResourceIterator;

/**
 * Base annotation discoverer.
 * 
 * @author animesh.kumar
 */
public abstract class Discoverer {

    /** map to hold ClassAnnotation listeners */
    private final Map<String, Set<ClassAnnotationDiscoveryListener>> classAnnotationListeners = 
        new HashMap<String, Set<ClassAnnotationDiscoveryListener>>();
    
    /** map to hold FieldAnnotation listeners */
    private final Map<String, Set<FieldAnnotationDiscoveryListener>> fieldAnnotationListeners = 
        new HashMap<String, Set<FieldAnnotationDiscoveryListener>>();
    
    /** map to hold MethodAnnotation listeners */
    private final Map<String, Set<MethodAnnotationDiscoveryListener>> methodAnnotationListeners = 
        new HashMap<String, Set<MethodAnnotationDiscoveryListener>>();
    
    /**
     * Instantiates a new Discoverer.
     */
    public Discoverer() {
    }

    /**
     * Adds ClassAnnotationDiscoveryListener
     * 
     * @param listener
     */
    public final void addAnnotationListener (ClassAnnotationDiscoveryListener listener) {
        addAnnotationListener (classAnnotationListeners, listener, listener.supportedAnnotations());
    }
    
    /**
     * Adds FieldAnnotationDiscoveryListener
     * 
     * @param listener
     */
    public final void addAnnotationListener (FieldAnnotationDiscoveryListener listener) {
        addAnnotationListener (fieldAnnotationListeners, listener, listener.supportedAnnotations());
    }

    /**
     * Adds MethodAnnotationDiscoveryListener
     * 
     * @param listener
     */
    public final void addAnnotationListener (MethodAnnotationDiscoveryListener listener) {
        addAnnotationListener (methodAnnotationListeners, listener, listener.supportedAnnotations());
    }
    
    /**
     * Helper class to find supported annotations of a listener and register them
     * 
     * @param <L>
     * @param map
     * @param listener
     * @param annotations
     */
    private <L> void addAnnotationListener (Map<String, Set<L>> map, L listener, String... annotations) {
        // throw exception if the listener doesn't support any annotations. what's the point of
        // registering then?
        if (null == annotations || annotations.length == 0) {
            throw new IllegalArgumentException(listener.getClass() + " has no supporting Annotations. Check method supportedAnnotations");
        }
        
        for (String annotation : annotations) {
            Set<L> listeners = map.get(annotation);
            if (null == listeners) {
                listeners = new HashSet<L>();
                map.put(annotation, listeners);
            }
            listeners.add(listener);
        }
    }
    
    /**
     * Gets the filter implementation.
     * 
     * @return the filter
     */
    public abstract Filter getFilter();

    /**
     * Finds resources to scan for
     */
    public abstract URL[] findResources();
    
    
    /**
     * that's my buddy! this is where all the discovery starts.
     */
    public final void discover(boolean classes, boolean fields, boolean methods, boolean visible, boolean invisible) {
        URL[] resources = findResources();
        for (URL resource : resources) {
            try {
                ResourceIterator itr = getResourceIterator(resource, getFilter());
                if (itr != null) {
                    InputStream is = null;
                    while ((is = itr.next()) != null) {
                        // make a data input stream
                        DataInputStream dstream = new DataInputStream(new BufferedInputStream(is));
                        try {
                            // get java-assist class file
                            ClassFile classFile = new ClassFile(dstream);
                        
                            // discover class-level annotations
                            if (classes) discoverAndIntimateForClassAnnotations (classFile, visible, invisible);
                            // discover field annotations
                            if (fields)  discoverAndIntimateForFieldAnnotations (classFile, visible, invisible);
                            // discover method annotations
                            if (methods) discoverAndIntimateForMethodAnnotations(classFile, visible, invisible);
                        } finally {
                             dstream.close();
                             is.close();
                        }
                    }
                }
            } catch (IOException e) {
                // TODO: Do something with this exception
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Discovers Class Annotations
     * 
     * @param classFile
     */
    private void discoverAndIntimateForClassAnnotations (ClassFile classFile, boolean visible, boolean invisible) {
        Set<Annotation> annotations = new HashSet<Annotation>();

        if (visible) {
            AnnotationsAttribute visibleA = (AnnotationsAttribute) classFile.getAttribute(AnnotationsAttribute.visibleTag);
            if (visibleA != null) annotations.addAll(Arrays.asList(visibleA.getAnnotations()));
        }

        if (invisible) {
            AnnotationsAttribute invisibleA = (AnnotationsAttribute) classFile.getAttribute(AnnotationsAttribute.invisibleTag);
            if (invisibleA != null) annotations.addAll(Arrays.asList(invisibleA.getAnnotations()));
        }
        
        // now tell listeners
        for (Annotation annotation : annotations) {
            Set<ClassAnnotationDiscoveryListener> listeners = classAnnotationListeners.get(annotation.getTypeName());
            if (null == listeners) {
                continue;
            }

            for (ClassAnnotationDiscoveryListener listener : listeners) {
                listener.discovered(classFile.getName(), annotation.getTypeName());
            }
        }
    }
    
    /**
     * Discovers Field Annotations
     * 
     * @param classFile
     */
    private void discoverAndIntimateForFieldAnnotations (ClassFile classFile, boolean visible, boolean invisible) {
        @SuppressWarnings("unchecked") 
        List<FieldInfo> fields = classFile.getFields();
        if (fields == null) {
            return;
        }
        
        for (FieldInfo fieldInfo : fields) {
            Set<Annotation> annotations = new HashSet<Annotation>();

            if (visible) {
                AnnotationsAttribute visibleA = (AnnotationsAttribute) fieldInfo.getAttribute(AnnotationsAttribute.visibleTag);
                if (visibleA != null) annotations.addAll(Arrays.asList(visibleA.getAnnotations()));
            }

            if (invisible) {
                AnnotationsAttribute invisibleA = (AnnotationsAttribute) fieldInfo.getAttribute(AnnotationsAttribute.invisibleTag);
                if (invisibleA != null) annotations.addAll(Arrays.asList(invisibleA.getAnnotations()));
            }
            
            // now tell listeners
            for (Annotation annotation : annotations) {
                Set<FieldAnnotationDiscoveryListener> listeners = fieldAnnotationListeners.get(annotation.getTypeName());
                if (null == listeners) {
                    continue;
                }
                
                for (FieldAnnotationDiscoveryListener listener : listeners) {
                    listener.discovered(classFile.getName(), fieldInfo.getName(), annotation.getTypeName());
                }
            }
        }
    }
    
    /**
     * Discovers Method Annotations
     * 
     * @param classFile
     */
    private void discoverAndIntimateForMethodAnnotations(ClassFile classFile, boolean visible, boolean invisible) {
        @SuppressWarnings("unchecked") 
        List<MethodInfo> methods = classFile.getMethods();
        if (methods == null) {
            return;
        }
        
        for (MethodInfo methodInfo : methods) {
            Set<Annotation> annotations = new HashSet<Annotation>();

            if (visible) {
                AnnotationsAttribute visibleA = (AnnotationsAttribute) methodInfo.getAttribute(AnnotationsAttribute.visibleTag);
                if (visibleA != null) annotations.addAll(Arrays.asList(visibleA.getAnnotations()));
            }

            if (invisible) {
                AnnotationsAttribute invisibleA = (AnnotationsAttribute) methodInfo.getAttribute(AnnotationsAttribute.invisibleTag);
                if (invisibleA != null) annotations.addAll(Arrays.asList(invisibleA.getAnnotations()));
            }

            // now tell listeners
            for (Annotation annotation : annotations) {
                Set<MethodAnnotationDiscoveryListener> listeners = methodAnnotationListeners.get(annotation.getTypeName());
                if (null == listeners) {
                    continue;
                }

                for (MethodAnnotationDiscoveryListener listener : listeners) {
                    listener.discovered(classFile.getName(), methodInfo.getName(), annotation.getTypeName());
                }
            }
        }    
    }


    /**
     * Gets the Resource iterator for URL with Filter.
     * 
     * @param url
     * @param filter
     * @return
     * @throws IOException
     */
    private ResourceIterator getResourceIterator(URL url, Filter filter) throws IOException {
        String urlString = url.toString();
        if (urlString.endsWith("!/")) {
            urlString = urlString.substring(4);
            urlString = urlString.substring(0, urlString.length() - 2);
            url = new URL(urlString);
        }

        if (!urlString.endsWith("/")) {
            return new JarFileIterator(url.openStream(), filter);
        } else {

            if (!url.getProtocol().equals("file")) {
                throw new IOException("Unable to understand protocol: " + url.getProtocol());
            }

            String filePath = URLDecoder.decode(url.getPath(), "UTF-8");
            File f = new File(filePath);
            if (!f.exists()) return null;

            if (f.isDirectory()) {
                return new ClassFileIterator(f, filter);
            } else {
                return new JarFileIterator(url.openStream(), filter);
            }
        }
    }
}
