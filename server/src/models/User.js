const mongoose = require("mongoose");
const validator = require("validator");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const Double = require('mongoose-double')(mongoose);

const userSchema = mongoose.Schema({
  name: {
    type: String,
    required: true,
    trim: true
  },
  email: {
    type: String,
    required: true,
    unique: true,
    lowercase: true,
    validate: value => {
      if (!validator.isEmail(value)) {
        throw new Error({
          error: "Invalid Email address"
        });
      }
    }
  },
  password: {
    type: String,
    required: true,
    minLength: 6
  },
  levels: {
    type: Number,
    required: true,
    min: 1,
    max: 11,
    default: 1
  },
  xp: {
    type: Number,
    required: true,
    min: 1,
    max: 255,
    default: 1
  },
  seeds: [
    {
      light: {
        type: Double,
        require: true
      },
      pressure: {
        type: Double,
        required: true
      },
      temp: {
        type: Double,
        required: true
      }

    }
  ],
  tokens: [
    {
      token: {
        type: String,
        required: true
      }
    }
  ]
});

userSchema.pre("save", async function(next) {
  // Hash the password before saving the user model
  const user = this;
  if (user.isModified("password")) {
    user.password = await bcrypt.hash(user.password, 8);
  }
  next();
});

userSchema.methods.generateAuthToken = async function() {
  // Generate an auth token for the user
  const user = this;
  const token = jwt.sign(
    {
      _id: user._id
    },
    process.env.JWT_KEY
  );
  user.tokens = user.tokens.concat({
    token
  });
  await user.save();
  return token;
};

userSchema.statics.findByEmail = async email => {
  const user = await User.findOne({
    email
  });
  if (!user) {
    throw new Error({
      error: "Invalid Email"
    });
  }
  return user;
};

userSchema.statics.findByCredentials = async (email, password) => {
  // Search for a user by email and password.
  const user = await User.findOne({
    email
  });
  if (!user) {
    throw new Error({
      error: "Invalid login credentials"
    });
  }
  const isPasswordMatch = await bcrypt.compare(password, user.password);
  if (!isPasswordMatch) {
    throw new Error({
      error: "Invalid login credentials"
    });
  }
  return user;
};

const User = mongoose.model("User", userSchema);

module.exports = User;
