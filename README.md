# Blog API

This application is an API for the various functions of my blog. It is backed by a Git repository and provides a Lucene index. 

## Outstanding Things to do 

* figure out how to configure the CORS hosts with properties 
* figure out how to configure the `/media` API host for the rewrite that happens in `MediaRestController`
* figure out how to configure the Github Webhook for whenever the git repository is changed and the index needs to be rebuilt
* figure out how 
* pagination for the blog 

## Some Useful Queries for Debugging 

If you want to examine what the GraphQL engine knows about your types, you might issue the following query, which will enumerate things like the types, their fields, and their fields' names. 

``` 
{
  __schema {
    types {
      name
      fields {
        name
        type {
          name
          kind
        }
      }
    }
  }
}

```