# Blog API


This application is an API for the various functions of my blog. It is backed by a Git repository and provides a Lucene index. 

## Outstanding Things to do 

* figure out how to configure the CORS hosts with properties.
* figure out how to configure the `/media` API host for the rewrite that happens in `MediaRestController`
* figure out how to configure the Github Webhook for whenever the git repository is changed and the index needs to be rebuilt
* figure out pagination for the blog.
* figure out monthly links or a dropdown so I can see all blogs from january 2012, or something like that?

## Some Useful Queries for Debugging 

If you want to examine what the GraphQL engine knows about your types, you might issue the following query, 
which will enumerate things like the types, their fields, and their fields' names. 

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

## If Something is Invalid 

All of the following things have externalities that need to be managed. Once they're updated, something, somewhere, needs to then trigger a rebuild using this API's rebuild functionality. Ideally, all these tributary repositories would in turn trigger rebuilds, but they don't. So, if in doubt, just bump any random file in the [repository containing the content for joshlong.com](https://github.com/joshlong/joshlong.github.io-content). This is sure to trigger a rebuild. 

### Not Seeing the Latest Blog Posts? 

Check that you've committed [a valid file to the content repository](https://github.com/joshlong/joshlong.github.io-content). 
That in turn should kick off a Github Actions run that then triggers a rebuild on the API and that invalidates all cached state and reads everything anew.

### Not Seeing the Latest Spring Tips Live Episodes? 

Make sure that [the `spring-tips/site-generator` Github Action has run](https://github.com/spring-tips/site-generator/tree/master). It might be that the Github Action is expired and I need to manually bump it. This simply updates the [`episodes.json` file in this git repository](https://github.com/spring-tips/spring-tips.github.io). You'll see updates [on the SpringTipsLive.io `episodes.json` feed](https://springtipslive.io/episodes.json). 

TODO: there should be a Github Action on the repository with `episodes.json` that in turn triggers a rebuild of joshlong.com. That might be seen as an uncooth coupling of concerns, however. Do I really want the Spring Tips Live services to be aware of their representation on JoshLong.com?  

### Not Seeing the Latest Bootiful Podcast episode? 




### Not Seeing the Latest Appearances? 

Make sure the `appearances.json` feed has been updated. It may fail to do that if you haven't run [this appearances-processor](https://github.com/developer-advocacy/appearances-processor/actions/workflows/pythonapp.yml). Sometimes Github Actions needs a bump. Note that the content in the appearances carousel are not markdown-compliant. 
