Getting Started
===============

Setting up the Development Environment
--------------------------------------
* Install IntelliJ (https://www.jetbrains.com/idea/)
* Install Git (https://git-scm.com/downloads)
* Setup SSH Key (https://docs.github.com/en/enterprise-server@3.0/github/authenticating-to-github/connecting-to-github-with-ssh/generating-a-new-ssh-key-and-adding-it-to-the-ssh-agent)
  * Alternatively, use https://support.atlassian.com/bitbucket-cloud/docs/set-up-an-ssh-key/
    * Instead of Step 3, paste your public key here: https://github.com/settings/keys
* Install Java 16 JDK (or higher)
  * https://www.oracle.com/java/technologies/downloads/#java16
* Navigate to where your environment will be and clone the Evolution repo
  * `git clone git@github.com:awesomelemonade/Evolution.git`
* Import project (File -> New -> Project from Existing Sources -> Select Evolution/pom.xml)
  * In IntelliJ, set the Project SDK to the downloaded Java 16 JDK
    * File -> Project Structure -> Project Settings -> Project -> Project SDK
* Run project
  * Open up `src/main/java/lemon/evolution/Evolution.java` in IntelliJ
  * Click on the green arrow to the left of the main method -> Run 'Evolution.main()'
* Run tests
  * Open up IntelliJ's project explorer
  * Right click src/test/java -> Run 'All Tests'
    * ![RunAllTests](doc-images/Evolution-RunAllTests.PNG)
* To update the code from GitHub (also known as remote)
  * `cd path/to/Evolution/`
  * `git pull origin master`

Trello Guide
------------
Red = Bug, Orange = On Hold, Blue = Large Task, Purple = Small Task

Categories
* New: Items that have not been seen yet.
* Prioritized: Items that have been seen (and color labeled)
* Selected for Dev: Items that are prioritized to be done in the near future
* In Progress: Items that someone is working on
* Completed: Items that are completed

Feel free to add anything to the "New" category. Any item that is not "on hold" can be worked on if you are interested in it.

Merging your work
-----------------

* Create a new branch
  ```
  git branch <name-of-branch>
  git checkout <name-of-branch>
  ```
* Commit
  ```
  git add <file-path>
  git add <file-path2>
  ...
  git commit -m 'Message'
  ```
  * Pro Tip: You can use `git add .` to add everything. Ensure that you added only the files you want with `git status`
* Push
  ```
  git push origin <name-of-branch>
  ```
* Create a pull request
  * Go to https://github.com/awesomelemonade/Evolution/pulls
  * New pull request -> Select <name-of-branch>
  * Request Code Review
  * Ensure no unit tests are failing (results should be shown on your PR page)