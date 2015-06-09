#!/usr/bin/env ruby

# Copyright (c) 2013 to Jason van Zyl
# All rights reserved. This program and the accompanying materials                                                                                                                                          
# are made available under the terms of the Eclipse Public License v1.0                                                                                                                                     
# which accompanies this distribution, and is available at                                                                                                                                                  
# http://www.eclipse.org/legal/epl-v10.html   

require 'fileutils'
require 'mustache'
require 'rexml/document'
require 'yaml'
include REXML

product = YAML.load_file('product.yml')

repositories = []
features = []
featureSets = []

product['repos'].each do |repo|
  repositories << repo
end

product['featureSets'].each do |featureSetHolder|
  featureSetHolder.each do |id,featureSet|
    unless featureSet['repo'].nil?
      repository = { 'id' => id, 'url' => featureSet['repo'] }
      repositories << repository
    end
    featureSets << featureSet
    featureSet['features'].each do |feature|
      features << feature
    end
  end
end

# We need to extract the repository and feature from each feature definition and
# make it available in the product.
product['repositories'] = repositories
product['features'] = features

# If the values are not copied into another Hash the mustache templates don't work?
vars = {}
product.each do |k,v|
  vars[k] = v
end

def template(templateFile, target, vars)
  template = File.open(templateFile).read
  renderedText = Mustache.render(template, vars)  
  File.open(target, 'w') { |f| 
    f.write(renderedText) 
  }
end

template('templates/pom.parent.mustache', 'pom.xml', vars)
template('templates/plugin.mustache', product['pluginId'] + "/plugin.xml", vars)
template('templates/pom.plugin.mustache', product['pluginId'] + "/pom.xml", vars)
template('templates/feature.mustache', product['featureId'] + "/feature.xml", vars)
template('templates/pom.feature.mustache', product['featureId'] + "/pom.xml", vars)

productId = product['productId']

template('templates/product.mustache', "#{productId}/#{productId}", vars)
template('templates/pom.product.mustache', "#{productId}/pom.xml", vars)

# Run Maven
system("mvn clean package")

# Copy the lombok.jar into place
FileUtils.cp("lombok.jar", "io.tesla.ide.product/target/products/io.tesla.ide.product/win32/win32/x86_64")

