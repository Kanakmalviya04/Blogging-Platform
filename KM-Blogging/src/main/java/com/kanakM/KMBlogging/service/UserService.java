package com.kanakM.KMBlogging.service;

import com.kanakM.KMBlogging.model.*;
import com.kanakM.KMBlogging.model.dto.SignInInput;
import com.kanakM.KMBlogging.model.dto.SignUpOutput;
import com.kanakM.KMBlogging.repository.IUserRepo;
import com.kanakM.KMBlogging.service.emailUtility.EmailHandler;
import com.kanakM.KMBlogging.service.hashingUtility.PasswordEncrypter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    IUserRepo userRepo;

    @Autowired
    AuthenticationService authenticationService;

    @Autowired
    PostService postService;

    @Autowired
    CommentService commentService;

    @Autowired
    FollowService followService;

    public SignUpOutput signUpUser(User user) {

        boolean signUpStatus = true;
        String signUpStatusMessage = null;

        String newEmail = user.getUserEmail();

        if(newEmail == null)
        {
            signUpStatusMessage = "Invalid email";
            signUpStatus = false;
            return new SignUpOutput(signUpStatus,signUpStatusMessage);
        }

        //check if this user email already exists ??
        User existingUser = userRepo.findFirstByUserEmail(newEmail);

        if(existingUser != null)
        {
            signUpStatusMessage = "Email already registered!!!";
            signUpStatus = false;
            return new SignUpOutput(signUpStatus,signUpStatusMessage);
        }

        //hash the password: encrypt the password
        try {
            String encryptedPassword = PasswordEncrypter.encryptPassword(user.getUserPassword());

            //saveAppointment the user with the new encrypted password

            user.setUserPassword(encryptedPassword);
            userRepo.save(user);

            return new SignUpOutput(signUpStatus, "User registered successfully!!!");
        }
        catch(Exception e)
        {
            signUpStatusMessage = "Internal error occurred during sign up";
            signUpStatus = false;
            return new SignUpOutput(signUpStatus,signUpStatusMessage);
        }
    }


    public String signInUser(SignInInput signInInput) {


        String signInStatusMessage = null;

        String signInEmail = signInInput.getEmail();

        if(signInEmail == null)
        {
            signInStatusMessage = "Invalid email";
            return signInStatusMessage;


        }

        //check if this user email already exists ??
        User existingUser = userRepo.findFirstByUserEmail(signInEmail);

        if(existingUser == null)
        {
            signInStatusMessage = "Email not registered!!!";
            return signInStatusMessage;

        }

        //match passwords :

        //hash the password: encrypt the password
        try {
            String encryptedPassword = PasswordEncrypter.encryptPassword(signInInput.getPassword());
            if(existingUser.getUserPassword().equals(encryptedPassword))
            {
                //session should be created since password matched and user id is valid
                AuthenticationToken authToken  = new AuthenticationToken(existingUser);
                authenticationService.saveAuthToken(authToken);

                EmailHandler.sendEmail(signInEmail,"Token for Kanak's Blogging API !!!",authToken.getTokenValue());
                return "Token sent to your email";
            }
            else {
                signInStatusMessage = "Invalid credentials!!!";
                return signInStatusMessage;
            }
        }
        catch(Exception e)
        {
            signInStatusMessage = "Internal error occurred during sign in";
            return signInStatusMessage;
        }

    }


    public String sigOutUser(String email) {

        User user = userRepo.findFirstByUserEmail(email);
        AuthenticationToken token = authenticationService.findFirstByUser(user);
        authenticationService.removeToken(token);
        return "User Signed out successfully, Come Again!!!";
    }


    public String createInstaPost(Post post, String email) {

        User postOwner = userRepo.findFirstByUserEmail(email);
        post.setPostOwner(postOwner);
        return postService.createInstaPost(post);
    }

    public String removeInstaPost(Integer postId,String email) {

        User user = userRepo.findFirstByUserEmail(email);
        return postService.removeInstaPost(postId,user);
    }

    public String addComment(Comment comment,String commenterEmail) {

        boolean postValid = postService.validatePost(comment.getInstaPost());
        if(postValid) {
            User commenter = userRepo.findFirstByUserEmail(commenterEmail);
            comment.setCommenter(commenter);
            return commentService.addComment(comment);
        }
        else {
            return "Cannot comment on Invalid Post!!";
        }
    }

    boolean authorizeCommentRemover(String email,Comment comment)
    {
        String  commentOwnerEmail = comment.getCommenter().getUserEmail();
        String  postOwnerEmail  = comment.getInstaPost().getPostOwner().getUserEmail();

        return postOwnerEmail.equals(email) || commentOwnerEmail.equals(email);
    }

    public String removeInstaComment(Integer commentId, String email) {
        Comment comment  = commentService.findComment(commentId);
        if(comment!=null)
        {
            if(authorizeCommentRemover(email,comment))
            {
                commentService.removeComment(comment);
                return "comment deleted successfully";
            }
            else
            {
                return "Unauthorized delete detected...Not allowed!!!!";
            }

        }
        else
        {
            return "Invalid Comment";
        }
    }

    public String followUser(Follow follow, String followerEmail) {


        User followTargetUser = userRepo.findById(follow.getCurrentUser().getUserId()).orElse(null);

        User follower = userRepo.findFirstByUserEmail(followerEmail);

        if(followTargetUser!=null)
        {
            if(followService.isFollowAllowed(followTargetUser,follower))
            {
                followService.startFollowing(follow,follower);
                return follower.getUserHandle()  + " is now following " + followTargetUser.getUserHandle();
            }
            else {
                return follower.getUserHandle()  + " already follows " + followTargetUser.getUserHandle();
            }
        }
        else {
            return "User to be followed is Invalid!!!";
        }


    }

    private boolean authorizeUnfollow(String email, Follow follow) {

        String  targetEmail = follow.getCurrentUser().getUserEmail();
        String  followerEmail  = follow.getCurrentUserFollower().getUserEmail();

        return targetEmail.equals(email) || followerEmail.equals(email);
    }

    public String unFollowUser(Integer followId, String followerEmail) {

        Follow follow  = followService.findFollow(followId);
        if(follow != null)
        {
            if(authorizeUnfollow(followerEmail,follow))
            {
                followService.unfollow(follow);
                return follow.getCurrentUser().getUserHandle() + "not followed by " + followerEmail;
            }
            else
            {
                return "Unauthorized unfollow detected...Not allowed!!!!";
            }
        }
        else
        {
            return "Invalid follow mapping";
        }
    }


}
